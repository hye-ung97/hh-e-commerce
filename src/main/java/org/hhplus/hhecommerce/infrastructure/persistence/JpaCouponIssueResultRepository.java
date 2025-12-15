package org.hhplus.hhecommerce.infrastructure.persistence;

import org.hhplus.hhecommerce.domain.coupon.CouponIssueResultRecord;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueResultRepository;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface JpaCouponIssueResultRepository extends JpaRepository<CouponIssueResultRecord, Long>, CouponIssueResultRepository {

    @Override
    Optional<CouponIssueResultRecord> findByRequestId(String requestId);

    @Override
    boolean existsByRequestId(String requestId);

    @Override
    boolean existsByCouponIdAndUserIdAndStatus(Long couponId, Long userId, CouponIssueStatus status);
}
