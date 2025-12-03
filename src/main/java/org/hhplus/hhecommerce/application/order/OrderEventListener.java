package org.hhplus.hhecommerce.application.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.application.ranking.UpdateProductRankingUseCase;
import org.hhplus.hhecommerce.domain.order.OrderCompletedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final UpdateProductRankingUseCase updateProductRankingUseCase;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        log.debug("주문 완료 이벤트 수신 - orderId: {}", event.orderId());

        try {
            event.productQuantityMap().forEach((productId, quantity) -> {
                if (productId > 0) {
                    updateProductRankingUseCase.execute(productId, quantity);
                }
            });
            log.debug("실시간 랭킹 업데이트 완료 - orderId: {}, products: {}",
                    event.orderId(), event.productQuantityMap().size());
        } catch (Exception e) {
            log.warn("실시간 랭킹 업데이트 실패 (주문에 영향 없음) - orderId: {}", event.orderId(), e);
        }
    }
}
