package org.hhplus.hhecommerce.application.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.order.PaymentCompletedEvent;
import org.hhplus.hhecommerce.infrastructure.external.ResilientExternalDataPlatformClient;
import org.hhplus.hhecommerce.infrastructure.external.ResilientNotificationClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final ResilientExternalDataPlatformClient externalDataPlatformClient;
    private final ResilientNotificationClient notificationClient;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDataPlatformTransfer(PaymentCompletedEvent event) {
        String threadName = Thread.currentThread().getName();
        log.debug("[{}] 데이터 플랫폼 전송 시작 - orderId: {}", threadName, event.orderId());

        long startTime = System.currentTimeMillis();
        try {
            externalDataPlatformClient.sendOrderData(event);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[{}] 데이터 플랫폼 전송 완료 - orderId: {}, duration: {}ms",
                    threadName, event.orderId(), duration);

        } catch (Exception e) {
            log.error("[{}] 데이터 플랫폼 전송 실패 (최종) - orderId: {}, error: {}",
                    threadName, event.orderId(), e.getMessage());
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotification(PaymentCompletedEvent event) {
        String threadName = Thread.currentThread().getName();
        log.debug("[{}] 알림톡 발송 시작 - orderId: {}", threadName, event.orderId());

        if (event.userPhone() == null || event.userPhone().isBlank()) {
            log.info("[{}] 알림톡 발송 생략 - orderId: {}, 전화번호 없음", threadName, event.orderId());
            return;
        }

        long startTime = System.currentTimeMillis();
        try {
            notificationClient.sendOrderConfirmation(event);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[{}] 알림톡 발송 완료 - orderId: {}, duration: {}ms",
                    threadName, event.orderId(), duration);

        } catch (Exception e) {
            log.error("[{}] 알림톡 발송 실패 (최종) - orderId: {}, error: {}",
                    threadName, event.orderId(), e.getMessage());
        }
    }
}
