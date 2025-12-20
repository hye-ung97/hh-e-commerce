package org.hhplus.hhecommerce.infrastructure.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.common.DomainEventPublisher;
import org.hhplus.hhecommerce.domain.common.OutboxEvent;
import org.hhplus.hhecommerce.domain.common.OutboxEventRepository;
import org.hhplus.hhecommerce.domain.order.OrderCompletedEvent;
import org.hhplus.hhecommerce.domain.order.PaymentCompletedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final DomainEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${outbox.relay.batch-size:100}")
    private int batchSize;

    @Value("${outbox.relay.max-retry-count:3}")
    private int maxRetryCount;

    @Value("${outbox.relay.cleanup-retention-days:7}")
    private int cleanupRetentionDays;

    @Value("${outbox.relay.processing-timeout-seconds:60}")
    private int processingTimeoutSeconds;

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:5000}")
    public void relayPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus.PENDING, batchSize);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Outbox 이벤트 발행 시작 - 대상: {}건", pendingEvents.size());

        int published = 0;
        int failed = 0;

        for (OutboxEvent event : pendingEvents) {
            try {
                publishEvent(event);
                published++;
            } catch (Exception e) {
                log.error("Outbox 이벤트 발행 실패 - id: {}, eventType: {}, error: {}",
                        event.getId(), event.getEventType(), e.getMessage());
                failed++;
            }
        }

        if (published > 0 || failed > 0) {
            log.info("Outbox 이벤트 발행 완료 - 성공: {}건, 실패: {}건", published, failed);
        }
    }

    public void publishEvent(OutboxEvent outboxEvent) {
        // 1. 카프카 발행 전 PROCESSING 상태로 먼저 변경 (별도 트랜잭션)
        //    이렇게 하면 발행 중에는 스케줄러가 다시 선택하지 않음
        transactionTemplate.executeWithoutResult(status -> {
            outboxEvent.markAsProcessing();
            outboxEventRepository.save(outboxEvent);
        });

        try {
            // 2. 카프카 발행
            Object event = deserializeEvent(outboxEvent);
            eventPublisher.publish(event);

            // 3. 발행 성공 → PUBLISHED (별도 트랜잭션)
            transactionTemplate.executeWithoutResult(status -> {
                outboxEvent.markAsPublished();
                outboxEventRepository.save(outboxEvent);
            });
            log.debug("Outbox 이벤트 발행 성공 - id: {}, eventType: {}",
                    outboxEvent.getId(), outboxEvent.getEventType());
        } catch (Exception e) {
            // 4. 발행 실패 → FAILED (별도 트랜잭션)
            transactionTemplate.executeWithoutResult(status -> {
                outboxEvent.markAsFailed(e.getMessage());
                outboxEventRepository.save(outboxEvent);
            });
            throw e;
        }
    }

    private Object deserializeEvent(OutboxEvent outboxEvent) {
        try {
            String eventType = outboxEvent.getEventType();
            String payload = outboxEvent.getPayload();

            return switch (eventType) {
                case "OrderCompletedEvent" -> objectMapper.readValue(payload, OrderCompletedEvent.class);
                case "PaymentCompletedEvent" -> objectMapper.readValue(payload, PaymentCompletedEvent.class);
                default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
            };
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event: " + outboxEvent.getEventType(), e);
        }
    }

    @Scheduled(fixedDelayString = "${outbox.relay.retry-delay-ms:60000}")
    public void retryFailedEvents() {
        List<OutboxEvent> failedEvents = outboxEventRepository
                .findFailedEventsForRetry(maxRetryCount, batchSize);

        if (failedEvents.isEmpty()) {
            return;
        }

        log.info("Outbox 실패 이벤트 재시도 시작 - 대상: {}건", failedEvents.size());

        int retried = 0;
        int maxRetriesExceeded = 0;

        for (OutboxEvent event : failedEvents) {
            if (!event.canRetry(maxRetryCount)) {
                maxRetriesExceeded++;
                log.error("[CRITICAL] Outbox 이벤트 최대 재시도 초과 - 수동 처리 필요. id: {}, eventType: {}, aggregateId: {}",
                        event.getId(), event.getEventType(), event.getAggregateId());
                continue;
            }

            try {
                event.retry();
                outboxEventRepository.save(event);
                publishEvent(event);
                retried++;
            } catch (Exception e) {
                log.warn("Outbox 이벤트 재시도 실패 - id: {}, retryCount: {}, error: {}",
                        event.getId(), event.getRetryCount(), e.getMessage());
            }
        }

        log.info("Outbox 실패 이벤트 재시도 완료 - 성공: {}건, 최대재시도초과: {}건", retried, maxRetriesExceeded);
    }

    @Scheduled(cron = "${outbox.relay.cleanup-cron:0 0 4 * * *}")
    @Transactional
    public void cleanupPublishedEvents() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(cleanupRetentionDays);
        int deleted = outboxEventRepository.deletePublishedEventsBefore(threshold);
        if (deleted > 0) {
            log.info("오래된 Outbox 발행 완료 이벤트 삭제 - {}건", deleted);
        }
    }

    @Scheduled(fixedDelayString = "${outbox.relay.stuck-check-delay-ms:30000}")
    public void recoverStuckProcessingEvents() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(processingTimeoutSeconds);
        List<OutboxEvent> stuckEvents = outboxEventRepository
                .findStuckProcessingEvents(threshold, batchSize);

        if (stuckEvents.isEmpty()) {
            return;
        }

        log.warn("PROCESSING 상태로 멈춘 이벤트 복구 시작 - 대상: {}건", stuckEvents.size());

        int recovered = 0;
        for (OutboxEvent event : stuckEvents) {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    event.retry();
                    outboxEventRepository.save(event);
                });
                recovered++;
                log.info("PROCESSING 이벤트 복구 완료 - id: {}, eventType: {}",
                        event.getId(), event.getEventType());
            } catch (Exception e) {
                log.error("PROCESSING 이벤트 복구 실패 - id: {}, error: {}",
                        event.getId(), e.getMessage());
            }
        }

        log.info("PROCESSING 상태 이벤트 복구 완료 - 복구: {}건", recovered);
    }
}
