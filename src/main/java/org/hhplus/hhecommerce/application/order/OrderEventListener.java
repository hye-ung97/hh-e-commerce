package org.hhplus.hhecommerce.application.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.application.ranking.UpdateProductRankingUseCase;
import org.hhplus.hhecommerce.domain.order.OrderCompletedEvent;
import org.hhplus.hhecommerce.infrastructure.cache.ProductCacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final UpdateProductRankingUseCase updateProductRankingUseCase;
    private final ProductCacheManager productCacheManager;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        String threadName = Thread.currentThread().getName();
        log.debug("[{}] 주문 완료 후처리 시작 - orderId: {}", threadName, event.orderId());

        long totalStartTime = System.currentTimeMillis();

        long cacheEvictionDuration = evictCaches(event, threadName);

        long rankingUpdateDuration = updateRanking(event, threadName);

        long totalDuration = System.currentTimeMillis() - totalStartTime;
        log.info("[{}] 주문 완료 후처리 완료 - orderId: {}, totalDuration: {}ms " +
                 "(cache: {}ms, ranking: {}ms)",
                threadName, event.orderId(), totalDuration,
                cacheEvictionDuration, rankingUpdateDuration);
    }

    private long evictCaches(OrderCompletedEvent event, String threadName) {
        long startTime = System.currentTimeMillis();
        try {
            productCacheManager.evictProductCaches();
            long duration = System.currentTimeMillis() - startTime;
            log.debug("[{}] 캐시 무효화 완료 - orderId: {}, duration: {}ms",
                    threadName, event.orderId(), duration);
            return duration;
        } catch (Exception e) {
            log.warn("[{}] 캐시 무효화 실패 (주문에 영향 없음) - orderId: {}, error: {}",
                    threadName, event.orderId(), e.getMessage());
            return -1;
        }
    }

    private long updateRanking(OrderCompletedEvent event, String threadName) {
        long startTime = System.currentTimeMillis();
        try {
            event.productQuantityMap().forEach((productId, quantity) -> {
                if (productId > 0) {
                    updateProductRankingUseCase.execute(productId, quantity);
                }
            });
            long duration = System.currentTimeMillis() - startTime;
            log.debug("[{}] 랭킹 업데이트 완료 - orderId: {}, products: {}, duration: {}ms",
                    threadName, event.orderId(), event.productQuantityMap().size(), duration);
            return duration;
        } catch (Exception e) {
            log.warn("[{}] 랭킹 업데이트 실패 (주문에 영향 없음) - orderId: {}, error: {}",
                    threadName, event.orderId(), e.getMessage());
            return -1;
        }
    }
}
