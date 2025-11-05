package org.hhplus.hhecommerce.infrastructure.repository.coupon;

import org.hhplus.hhecommerce.domain.coupon.*;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class MockCouponRepository implements CouponRepository {
    private final Map<Long, Coupon> store = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    public MockCouponRepository() {
        save(new Coupon("신규가입 10% 할인", CouponType.RATE, 10, 10000, 0, 100,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30)));
        save(new Coupon("5만원 할인 쿠폰", CouponType.AMOUNT, 50000, null, 100000, 50,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(30)));
    }

    @Override
    public Coupon save(Coupon coupon) {
        if (coupon.getId() == null) coupon.setId(idGenerator.getAndIncrement());
        store.put(coupon.getId(), coupon);
        return coupon;
    }

    @Override
    public Optional<Coupon> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Coupon> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public List<Coupon> findAll(int page, int size) {
        return store.values().stream()
                .sorted(Comparator.comparing(Coupon::getId))
                .skip((long) page * size)
                .limit(size)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    @Override
    public int countAll() {
        return store.size();
    }

    @Override
    public List<Coupon> findAvailableCoupons(LocalDateTime now) {
        return store.values().stream()
                .filter(coupon -> isAvailable(coupon, now))
                .sorted(Comparator.comparing(Coupon::getId))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    @Override
    public List<Coupon> findAvailableCoupons(LocalDateTime now, int page, int size) {
        return store.values().stream()
                .filter(coupon -> isAvailable(coupon, now))
                .sorted(Comparator.comparing(Coupon::getId))
                .skip((long) page * size)
                .limit(size)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    @Override
    public int countAvailableCoupons(LocalDateTime now) {
        return (int) store.values().stream()
                .filter(coupon -> isAvailable(coupon, now))
                .count();
    }

    private boolean isAvailable(Coupon coupon, LocalDateTime now) {
        return now.isAfter(coupon.getStartAt())
                && now.isBefore(coupon.getEndAt())
                && coupon.getIssuedQuantity() < coupon.getTotalQuantity();
    }
}
