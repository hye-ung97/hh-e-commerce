package org.hhplus.hhecommerce.domain.coupon;

import org.hhplus.hhecommerce.domain.coupon.FailedCouponRollback.RollbackFailureStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FailedCouponRollbackRepository extends JpaRepository<FailedCouponRollback, Long> {

    List<FailedCouponRollback> findByStatus(RollbackFailureStatus status);

    @Query("SELECT f FROM FailedCouponRollback f " +
           "WHERE f.status = 'PENDING' AND f.retryCount < :maxRetryCount " +
           "ORDER BY f.createdAt ASC")
    List<FailedCouponRollback> findPendingForRetry(@Param("maxRetryCount") int maxRetryCount,
                                                    org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("DELETE FROM FailedCouponRollback f " +
           "WHERE f.status IN ('RESOLVED', 'IGNORED') AND f.resolvedAt < :before")
    int deleteResolvedBefore(@Param("before") LocalDateTime before);

    @Query("SELECT COUNT(f) > 0 FROM FailedCouponRollback f " +
           "WHERE f.couponId = :couponId AND f.userId = :userId AND f.status = 'PENDING'")
    boolean existsPendingByCouponIdAndUserId(@Param("couponId") Long couponId,
                                              @Param("userId") Long userId);
}
