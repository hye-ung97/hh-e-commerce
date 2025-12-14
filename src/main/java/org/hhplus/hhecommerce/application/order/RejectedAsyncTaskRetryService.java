package org.hhplus.hhecommerce.application.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.application.ranking.UpdateProductRankingUseCase;
import org.hhplus.hhecommerce.domain.common.RejectedAsyncTask;
import org.hhplus.hhecommerce.domain.common.RejectedAsyncTaskRepository;
import org.hhplus.hhecommerce.domain.order.OrderCompletedEvent;
import org.hhplus.hhecommerce.domain.order.PaymentCompletedEvent;
import org.hhplus.hhecommerce.infrastructure.cache.ProductCacheManager;
import org.hhplus.hhecommerce.infrastructure.external.ExternalDataPlatformClient;
import org.hhplus.hhecommerce.infrastructure.external.NotificationClient;
import org.hhplus.hhecommerce.infrastructure.external.ResilientNotificationClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RejectedAsyncTaskRetryService {

    private static final int MAX_RETRY_COUNT = 5;
    private static final int BATCH_SIZE = 100;

    private final RejectedAsyncTaskRepository rejectedAsyncTaskRepository;
    private final ExternalDataPlatformClient externalDataPlatformClient;
    private final NotificationClient notificationClient;
    private final UpdateProductRankingUseCase updateProductRankingUseCase;
    private final ProductCacheManager productCacheManager;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 300000) // 5분
    public void retryRejectedTasks() {
        log.debug("거부된 비동기 작업 재처리 시작");

        List<RejectedAsyncTask> pendingTasks = rejectedAsyncTaskRepository
                .findPendingForRetry(MAX_RETRY_COUNT, PageRequest.of(0, BATCH_SIZE));

        if (pendingTasks.isEmpty()) {
            log.debug("재처리할 거부된 작업 없음");
            return;
        }

        log.info("거부된 비동기 작업 재처리 시작 - 대상: {}건", pendingTasks.size());

        int success = 0;
        int failed = 0;
        int exceeded = 0;

        for (RejectedAsyncTask task : pendingTasks) {
            try {
                RetryResult result = processRetry(task);
                switch (result) {
                    case SUCCESS -> success++;
                    case FAILED -> failed++;
                    case MAX_RETRY_EXCEEDED -> exceeded++;
                }
            } catch (Exception e) {
                log.error("작업 재처리 중 오류 - id: {}, taskType: {}, error: {}",
                        task.getId(), task.getTaskType(), e.getMessage(), e);
                failed++;
            }
        }

        log.info("거부된 비동기 작업 재처리 완료 - 성공: {}건, 실패: {}건, 최대재시도초과: {}건",
                success, failed, exceeded);
    }

    @Transactional
    public RetryResult processRetry(RejectedAsyncTask task) {
        if (!task.canRetry(MAX_RETRY_COUNT)) {
            task.markAsFailed();
            rejectedAsyncTaskRepository.save(task);
            log.error("최대 재시도 횟수 초과 - 수동 처리 필요. id: {}, taskType: {}",
                    task.getId(), task.getTaskType());
            return RetryResult.MAX_RETRY_EXCEEDED;
        }

        task.startRetry();
        rejectedAsyncTaskRepository.save(task);

        try {
            executeTask(task);

            task.complete();
            rejectedAsyncTaskRepository.save(task);
            log.info("작업 재처리 성공 - id: {}, taskType: {}", task.getId(), task.getTaskType());
            return RetryResult.SUCCESS;

        } catch (Exception e) {
            task.fail(e.getMessage());

            if (!task.canRetry(MAX_RETRY_COUNT)) {
                task.markAsFailed();
                log.error("최대 재시도 횟수 도달 - id: {}, taskType: {}, retryCount: {}",
                        task.getId(), task.getTaskType(), task.getRetryCount());
            } else {
                log.warn("작업 재처리 실패 - 재시도 예정. id: {}, taskType: {}, retryCount: {}",
                        task.getId(), task.getTaskType(), task.getRetryCount());
            }

            rejectedAsyncTaskRepository.save(task);
            return RetryResult.FAILED;
        }
    }

    private void executeTask(RejectedAsyncTask task) throws Exception {
        switch (task.getTaskType()) {
            case PaymentEventListener.TASK_TYPE_DATA_PLATFORM -> {
                PaymentCompletedEvent event = objectMapper.readValue(
                        task.getEventPayload(), PaymentCompletedEvent.class);
                externalDataPlatformClient.sendOrderData(event);
            }
            case PaymentEventListener.TASK_TYPE_NOTIFICATION,
                 ResilientNotificationClient.TASK_TYPE_NOTIFICATION_FAILED -> {
                PaymentCompletedEvent event = objectMapper.readValue(
                        task.getEventPayload(), PaymentCompletedEvent.class);
                notificationClient.sendOrderConfirmation(event);
            }
            case OrderEventListener.TASK_TYPE_ORDER_COMPLETED -> {
                OrderCompletedEvent event = objectMapper.readValue(
                        task.getEventPayload(), OrderCompletedEvent.class);
                processOrderCompletedTask(event);
            }
            case OrderEventListener.TASK_TYPE_RANKING_UPDATE_FAILED -> {
                OrderCompletedEvent event = objectMapper.readValue(
                        task.getEventPayload(), OrderCompletedEvent.class);
                processRankingUpdateTask(event);
            }
            default -> throw new IllegalArgumentException("Unknown task type: " + task.getTaskType());
        }
    }

    private void processOrderCompletedTask(OrderCompletedEvent event) {
        productCacheManager.evictProductCaches();
        processRankingUpdateTask(event);
    }

    private void processRankingUpdateTask(OrderCompletedEvent event) {
        event.productQuantityMap().forEach((productId, quantity) -> {
            if (productId > 0) {
                updateProductRankingUseCase.execute(productId, quantity);
            }
        });
    }

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void cleanupOldRecords() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        int deleted = rejectedAsyncTaskRepository.deleteCompletedBefore(threshold);
        if (deleted > 0) {
            log.info("오래된 거부된 작업 기록 삭제 - {}건", deleted);
        }
    }

    public enum RetryResult {
        SUCCESS,
        FAILED,
        MAX_RETRY_EXCEEDED
    }
}
