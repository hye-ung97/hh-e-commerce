package org.hhplus.hhecommerce.domain.coupon;

import java.time.LocalDateTime;

public record CouponIssueRequest(
        String requestId,
        Long couponId,
        Long userId,
        LocalDateTime requestedAt
) {
    public static CouponIssueRequest of(String requestId, Long couponId, Long userId) {
        return new CouponIssueRequest(requestId, couponId, userId, LocalDateTime.now());
    }
}
