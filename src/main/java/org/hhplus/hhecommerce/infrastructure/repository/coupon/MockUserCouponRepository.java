package org.hhplus.hhecommerce.infrastructure.repository.coupon;

import org.hhplus.hhecommerce.domain.coupon.*;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class MockUserCouponRepository implements UserCouponRepository {
    private final Map<Long, UserCoupon> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public UserCoupon save(UserCoupon userCoupon) {
        if (userCoupon.getId() == null) userCoupon.setId(idGenerator.getAndIncrement());
        store.put(userCoupon.getId(), userCoupon);
        return userCoupon;
    }

    @Override
    public Optional<UserCoupon> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<UserCoupon> findByUserId(Long userId) {
        return store.values().stream()
                .filter(uc -> uc.getUserId().equals(userId))
                .sorted(Comparator.comparing(UserCoupon::getId))
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
        return store.values().stream()
                .anyMatch(uc -> uc.getUserId().equals(userId)
                        && uc.getCouponId().equals(couponId));
    }

    @Override
    public List<UserCoupon> findAvailableByUserId(Long userId, LocalDateTime now) {
        return store.values().stream()
                .filter(uc -> uc.getUserId().equals(userId))
                .filter(uc -> uc.getStatus() == CouponStatus.AVAILABLE)
                .filter(uc -> uc.getExpiredAt().isAfter(now))
                .sorted(Comparator.comparing(UserCoupon::getId))
                .collect(Collectors.toList());
    }
}
