package org.hhplus.hhecommerce.domain.coupon;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserCouponRepository {
    UserCoupon save(UserCoupon userCoupon);

    Optional<UserCoupon> findById(Long id);

    List<UserCoupon> findByUserId(Long userId);

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    List<UserCoupon> findAvailableByUserId(Long userId, LocalDateTime now);
}
