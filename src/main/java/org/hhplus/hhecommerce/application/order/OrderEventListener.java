package org.hhplus.hhecommerce.application.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.application.ranking.UpdateProductRankingUseCase;
import org.hhplus.hhecommerce.domain.common.RejectedAsyncTask;
import org.hhplus.hhecommerce.domain.common.RejectedAsyncTaskRepository;
import org.hhplus.hhecommerce.domain.order.OrderCompletedEvent;
import org.hhplus.hhecommerce.infrastructure.cache.ProductCacheManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
public class OrderEventListener {

    public static final String TASK_TYPE_ORDER_COMPLETED = "ORDER_COMPLETED";
    public static final String TASK_TYPE_RANKING_UPDATE_FAILED = "RANKING_UPDATE_FAILED";

    private final UpdateProductRankingUseCase updateProductRankingUseCase;
    private final ProductCacheManager productCacheManager;
    private final Executor taskExecutor;
    private final RejectedAsyncTaskRepository rejectedAsyncTaskRepository;
    private final ObjectMapper objectMapper;

    public OrderEventListener(
            UpdateProductRankingUseCase updateProductRankingUseCase,
            ProductCacheManager productCacheManager,
            @Qualifier("taskExecutor") Executor taskExecutor,
            RejectedAsyncTaskRepository rejectedAsyncTaskRepository,
            ObjectMapper objectMapper) {
        this.updateProductRankingUseCase = updateProductRankingUseCase;
        this.productCacheManager = productCacheManager;
        this.taskExecutor = taskExecutor;
        this.rejectedAsyncTaskRepository = rejectedAsyncTaskRepository;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        try {
            taskExecutor.execute(() -> processOrderCompleted(event));
        } catch (RejectedExecutionException e) {
            log.warn("주문 완료 후처리 작업 거부됨 - DLQ에 저장합니다. orderId: {}", event.orderId());
            saveToDeadLetterQueue(event, TASK_TYPE_ORDER_COMPLETED, e.getMessage());
        }
    }

    private void processOrderCompleted(OrderCompletedEvent event) {
        log.debug("주문 완료 후처리 시작 - orderId: {}", event.orderId());

        long totalStartTime = System.currentTimeMillis();

        long cacheEvictionDuration = evictCaches(event);
        long rankingUpdateDuration = updateRanking(event);

        long totalDuration = System.currentTimeMillis() - totalStartTime;
        log.info("주문 완료 후처리 완료 - orderId: {}, totalDuration: {}ms (cache: {}ms, ranking: {}ms)",
                event.orderId(), totalDuration, cacheEvictionDuration, rankingUpdateDuration);
    }

    private long evictCaches(OrderCompletedEvent event) {
        long startTime = System.currentTimeMillis();
        try {
            productCacheManager.evictProductCaches();
            long duration = System.currentTimeMillis() - startTime;
            log.debug("캐시 무효화 완료 - orderId: {}, duration: {}ms", event.orderId(), duration);
            return duration;
        } catch (Exception e) {
            log.warn("캐시 무효화 실패 (주문에 영향 없음) - orderId: {}, error: {}",
                    event.orderId(), e.getMessage());
            return -1;
        }
    }

    private long updateRanking(OrderCompletedEvent event) {
        long startTime = System.currentTimeMillis();
        try {
            event.productQuantityMap().forEach((productId, quantity) -> {
                if (productId > 0) {
                    updateProductRankingUseCase.execute(productId, quantity);
                }
            });
            long duration = System.currentTimeMillis() - startTime;
            log.debug("랭킹 업데이트 완료 - orderId: {}, products: {}, duration: {}ms",
                    event.orderId(), event.productQuantityMap().size(), duration);
            return duration;
        } catch (Exception e) {
            log.warn("랭킹 업데이트 실패 - DLQ에 저장합니다. orderId: {}, error: {}",
                    event.orderId(), e.getMessage());
            saveToDeadLetterQueue(event, TASK_TYPE_RANKING_UPDATE_FAILED, e.getMessage());
            return -1;
        }
    }

    private void saveToDeadLetterQueue(OrderCompletedEvent event, String taskType, String errorMessage) {
        try {
            String eventPayload = objectMapper.writeValueAsString(event);
            RejectedAsyncTask rejectedTask = new RejectedAsyncTask(taskType, eventPayload, errorMessage);
            rejectedAsyncTaskRepository.save(rejectedTask);
            log.info("DLQ에 저장 완료 - taskType: {}, orderId: {}", taskType, event.orderId());
        } catch (JsonProcessingException e) {
            log.error("DLQ 저장 실패 - 이벤트 직렬화 오류. orderId: {}, error: {}",
                    event.orderId(), e.getMessage());
        } catch (Exception e) {
            log.error("DLQ 저장 실패 - orderId: {}, error: {}",
                    event.orderId(), e.getMessage());
        }
    }
}
