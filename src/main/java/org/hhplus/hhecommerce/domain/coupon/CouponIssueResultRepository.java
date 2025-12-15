package org.hhplus.hhecommerce.domain.coupon;

import java.util.Optional;

public interface CouponIssueResultRepository {

    CouponIssueResultRecord save(CouponIssueResultRecord record);

    Optional<CouponIssueResultRecord> findByRequestId(String requestId);

    boolean existsByRequestId(String requestId);

    boolean existsByCouponIdAndUserIdAndStatus(Long couponId, Long userId, CouponIssueStatus status);
}
