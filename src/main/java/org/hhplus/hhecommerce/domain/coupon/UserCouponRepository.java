package org.hhplus.hhecommerce.domain.coupon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    List<UserCoupon> findByUserId(Long userId);

    List<UserCoupon> findByCouponId(Long couponId);

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    @Query("SELECT uc FROM UserCoupon uc WHERE uc.userId = :userId AND uc.status = 'AVAILABLE' AND uc.expiredAt >= :now")
    List<UserCoupon> findAvailableByUserId(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE UserCoupon uc SET uc.status = 'USED', uc.usedAt = CURRENT_TIMESTAMP " +
           "WHERE uc.id = :userCouponId AND uc.status = 'AVAILABLE'")
    int useCoupon(@Param("userCouponId") Long userCouponId);
}
