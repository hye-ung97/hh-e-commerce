package org.hhplus.hhecommerce.domain.coupon;

public interface CouponIssueManager {

    CouponIssueResult tryIssue(Long couponId, Long userId);

    boolean hasAlreadyIssued(Long couponId, Long userId);

    void confirm(Long couponId, Long userId);

    void rollback(Long couponId, Long userId);

    boolean shouldUpdateCouponStock();
}
