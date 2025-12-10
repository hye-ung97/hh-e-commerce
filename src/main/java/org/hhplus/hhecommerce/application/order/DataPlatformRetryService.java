package org.hhplus.hhecommerce.application.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.order.FailedDataPlatformEvent;
import org.hhplus.hhecommerce.domain.order.FailedDataPlatformEventRepository;
import org.hhplus.hhecommerce.domain.order.PaymentCompletedEvent;
import org.hhplus.hhecommerce.infrastructure.external.ExternalDataPlatformClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataPlatformRetryService {

    private static final int MAX_RETRY_COUNT = 5;
    private static final int BATCH_SIZE = 100;

    private final FailedDataPlatformEventRepository failedEventRepository;
    private final ExternalDataPlatformClient externalDataPlatformClient;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 300000) // 5분
    public void retryFailedEvents() {
        log.debug("데이터 플랫폼 실패 이벤트 재처리 시작");

        List<FailedDataPlatformEvent> pendingEvents = failedEventRepository
                .findPendingForRetry(MAX_RETRY_COUNT, PageRequest.of(0, BATCH_SIZE));

        if (pendingEvents.isEmpty()) {
            log.debug("재처리할 실패 이벤트 없음");
            return;
        }

        log.info("데이터 플랫폼 실패 이벤트 재처리 시작 - 대상: {}건", pendingEvents.size());

        int success = 0;
        int failed = 0;
        int exceeded = 0;

        for (FailedDataPlatformEvent event : pendingEvents) {
            try {
                RetryResult result = processRetry(event);
                switch (result) {
                    case SUCCESS -> success++;
                    case FAILED -> failed++;
                    case MAX_RETRY_EXCEEDED -> exceeded++;
                }
            } catch (Exception e) {
                log.error("이벤트 재처리 중 오류 - id: {}, error: {}",
                        event.getId(), e.getMessage(), e);
                failed++;
            }
        }

        log.info("데이터 플랫폼 실패 이벤트 재처리 완료 - 성공: {}건, 실패: {}건, 최대재시도초과: {}건",
                success, failed, exceeded);
    }

    @Transactional
    public RetryResult processRetry(FailedDataPlatformEvent failedEvent) {
        if (!failedEvent.canRetry(MAX_RETRY_COUNT)) {
            failedEvent.markAsFailed();
            failedEventRepository.save(failedEvent);
            log.error("최대 재시도 횟수 초과 - 수동 처리 필요. orderId: {}", failedEvent.getOrderId());
            return RetryResult.MAX_RETRY_EXCEEDED;
        }

        failedEvent.startRetry();
        failedEventRepository.save(failedEvent);

        try {
            PaymentCompletedEvent event = objectMapper.readValue(
                    failedEvent.getEventPayload(), PaymentCompletedEvent.class);

            externalDataPlatformClient.sendOrderData(event);

            failedEvent.complete();
            failedEventRepository.save(failedEvent);
            log.info("이벤트 재처리 성공 - orderId: {}", failedEvent.getOrderId());
            return RetryResult.SUCCESS;

        } catch (Exception e) {
            failedEvent.fail(e.getMessage());

            if (!failedEvent.canRetry(MAX_RETRY_COUNT)) {
                failedEvent.markAsFailed();
                log.error("최대 재시도 횟수 도달 - orderId: {}, retryCount: {}",
                        failedEvent.getOrderId(), failedEvent.getRetryCount());
            } else {
                log.warn("이벤트 재처리 실패 - 재시도 예정. orderId: {}, retryCount: {}",
                        failedEvent.getOrderId(), failedEvent.getRetryCount());
            }

            failedEventRepository.save(failedEvent);
            return RetryResult.FAILED;
        }
    }

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void cleanupOldRecords() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        int deleted = failedEventRepository.deleteCompletedBefore(threshold);
        if (deleted > 0) {
            log.info("오래된 데이터 플랫폼 실패 이벤트 삭제 - {}건", deleted);
        }
    }

    public enum RetryResult {
        SUCCESS,
        FAILED,
        MAX_RETRY_EXCEEDED
    }
}
