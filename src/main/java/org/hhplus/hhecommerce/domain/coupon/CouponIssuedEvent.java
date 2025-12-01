package org.hhplus.hhecommerce.domain.coupon;

public record CouponIssuedEvent(
        Long couponId,
        Long userId
) {
}
