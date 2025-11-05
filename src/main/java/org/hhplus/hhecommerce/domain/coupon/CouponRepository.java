package org.hhplus.hhecommerce.domain.coupon;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CouponRepository {
    Coupon save(Coupon coupon);

    Optional<Coupon> findById(Long id);

    List<Coupon> findAll();

    List<Coupon> findAll(int page, int size);

    int countAll();

    List<Coupon> findAvailableCoupons(LocalDateTime now);

    List<Coupon> findAvailableCoupons(LocalDateTime now, int page, int size);

    int countAvailableCoupons(LocalDateTime now);
}
