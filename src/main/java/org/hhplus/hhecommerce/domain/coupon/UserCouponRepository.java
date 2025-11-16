package org.hhplus.hhecommerce.domain.coupon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    List<UserCoupon> findByUserId(Long userId);

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    @Query("SELECT uc FROM UserCoupon uc WHERE uc.userId = :userId AND uc.status = 'AVAILABLE' AND uc.expiredAt >= :now")
    List<UserCoupon> findAvailableByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
