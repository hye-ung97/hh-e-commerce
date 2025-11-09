package org.hhplus.hhecommerce.application.coupon;

import org.hhplus.hhecommerce.api.dto.coupon.UserCouponListResponse;
import org.hhplus.hhecommerce.domain.coupon.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class GetUserCouponsUseCaseTest {

    private GetUserCouponsUseCase getUserCouponsUseCase;
    private TestCouponRepository couponRepository;
    private TestUserCouponRepository userCouponRepository;

    @BeforeEach
    void setUp() {
        couponRepository = new TestCouponRepository();
        userCouponRepository = new TestUserCouponRepository();
        getUserCouponsUseCase = new GetUserCouponsUseCase(couponRepository, userCouponRepository);
    }

    @Test
    @DisplayName("사용자의 쿠폰 목록을 조회할 수 있다")
    void 사용자의_쿠폰_목록을_조회할_수_있다() {
        // Given
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();

        Coupon coupon1 = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        Coupon coupon2 = new Coupon("5000원 할인", CouponType.AMOUNT, 5000, null, 30000, 100,
                now.minusDays(1), now.plusDays(30));
        couponRepository.save(coupon1);
        couponRepository.save(coupon2);

        UserCoupon userCoupon1 = new UserCoupon(userId, coupon1.getId(), now.plusDays(30));
        UserCoupon userCoupon2 = new UserCoupon(userId, coupon2.getId(), now.plusDays(30));
        userCouponRepository.save(userCoupon1);
        userCouponRepository.save(userCoupon2);

        // When
        UserCouponListResponse response = getUserCouponsUseCase.execute(userId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.coupons()).hasSize(2);
        assertThat(response.totalCount()).isEqualTo(2);
    }

    // 테스트 전용 Mock Repository
    static class TestCouponRepository implements CouponRepository {
        private final Map<Long, Coupon> store = new HashMap<>();
        private Long idCounter = 1L;

        @Override
        public Coupon save(Coupon coupon) {
            if (coupon.getId() == null) {
                coupon.setId(idCounter++);
            }
            store.put(coupon.getId(), coupon);
            return coupon;
        }

        @Override
        public Optional<Coupon> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Coupon> findAvailableCoupons(LocalDateTime now, int page, int size) {
            return store.values().stream()
                    .filter(c -> c.getStartAt().isBefore(now) && c.getEndAt().isAfter(now))
                    .filter(c -> c.getIssuedQuantity() < c.getTotalQuantity())
                    .skip((long) page * size)
                    .limit(size)
                    .collect(Collectors.toList());
        }

        @Override
        public int countAvailableCoupons(LocalDateTime now) {
            return (int) store.values().stream()
                    .filter(c -> c.getStartAt().isBefore(now) && c.getEndAt().isAfter(now))
                    .filter(c -> c.getIssuedQuantity() < c.getTotalQuantity())
                    .count();
        }

        @Override
        public List<Coupon> findAvailableCoupons(LocalDateTime now) {
            return new ArrayList<>();
        }

        @Override
        public List<Coupon> findAll() {
            return new ArrayList<>();
        }

        @Override
        public List<Coupon> findAll(int page, int size) {
            return new ArrayList<>();
        }

        @Override
        public int countAll() {
            return 0;
        }
    }

    static class TestUserCouponRepository implements UserCouponRepository {
        private final Map<Long, UserCoupon> store = new HashMap<>();
        private Long idCounter = 1L;

        @Override
        public UserCoupon save(UserCoupon userCoupon) {
            if (userCoupon.getId() == null) {
                userCoupon.setId(idCounter++);
            }
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
                    .collect(Collectors.toList());
        }

        @Override
        public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
            return store.values().stream()
                    .anyMatch(uc -> uc.getUserId().equals(userId) && uc.getCouponId().equals(couponId));
        }

        @Override
        public List<UserCoupon> findAvailableByUserId(Long userId, LocalDateTime now) {
            return store.values().stream()
                    .filter(uc -> uc.getUserId().equals(userId))
                    .filter(uc -> uc.getStatus() == CouponStatus.AVAILABLE)
                    .filter(uc -> uc.getExpiredAt().isAfter(now))
                    .collect(Collectors.toList());
        }
    }
}
