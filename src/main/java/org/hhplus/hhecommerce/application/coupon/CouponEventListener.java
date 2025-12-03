package org.hhplus.hhecommerce.application.coupon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueManager;
import org.hhplus.hhecommerce.domain.coupon.CouponIssuedEvent;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponEventListener {

    private final CouponIssueManager couponIssueManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 100))
    public void handleCouponIssued(CouponIssuedEvent event) {
        log.debug("쿠폰 발급 이벤트 수신 (AFTER_COMMIT) - couponId: {}, userId: {}",
                event.couponId(), event.userId());

        try {
            couponIssueManager.confirm(event.couponId(), event.userId());
            log.info("쿠폰 발급 확정 완료 - couponId: {}, userId: {}",
                    event.couponId(), event.userId());
        } catch (Exception e) {
            log.error("쿠폰 발급 확정 실패 - couponId: {}, userId: {}",
                    event.couponId(), event.userId(), e);
            throw e;
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleCouponRollback(CouponIssuedEvent event) {
        log.debug("쿠폰 발급 롤백 이벤트 수신 (AFTER_ROLLBACK) - couponId: {}, userId: {}",
                event.couponId(), event.userId());

        try {
            couponIssueManager.rollback(event.couponId(), event.userId());
            log.info("쿠폰 발급 롤백 완료 - couponId: {}, userId: {}",
                    event.couponId(), event.userId());
        } catch (Exception e) {
            log.error("쿠폰 발급 롤백 실패 - couponId: {}, userId: {}",
                    event.couponId(), event.userId(), e);
        }
    }
}
