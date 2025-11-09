package org.hhplus.hhecommerce.application.coupon;

import org.hhplus.hhecommerce.api.dto.coupon.AvailableUserCouponListResponse;
import org.hhplus.hhecommerce.domain.coupon.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GetAvailableUserCouponsUseCaseTest {

    private GetAvailableUserCouponsUseCase getAvailableUserCouponsUseCase;
    private TestCouponRepository couponRepository;
    private TestUserCouponRepository userCouponRepository;

    @BeforeEach
    void setUp() {
        couponRepository = new TestCouponRepository();
        userCouponRepository = new TestUserCouponRepository();
        getAvailableUserCouponsUseCase = new GetAvailableUserCouponsUseCase(couponRepository, userCouponRepository);
    }

    @Test
    @DisplayName("주문 금액에 사용 가능한 쿠폰 목록을 조회할 수 있다")
    void 주문_금액에_사용_가능한_쿠폰_목록을_조회할_수_있다() {
        // Given
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();

        // 최소 주문금액 10,000원
        Coupon coupon1 = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        // 최소 주문금액 30,000원
        Coupon coupon2 = new Coupon("5000원 할인", CouponType.AMOUNT, 5000, null, 30000, 100,
                now.minusDays(1), now.plusDays(30));
        couponRepository.save(coupon1);
        couponRepository.save(coupon2);

        UserCoupon userCoupon1 = new UserCoupon(userId, coupon1.getId(), now.plusDays(30));
        UserCoupon userCoupon2 = new UserCoupon(userId, coupon2.getId(), now.plusDays(30));
        userCouponRepository.save(userCoupon1);
        userCouponRepository.save(userCoupon2);

        // When - 주문금액 20,000원
        AvailableUserCouponListResponse response = getAvailableUserCouponsUseCase.execute(userId, 20000);

        // Then
        AvailableUserCouponListResponse.AvailableUserCouponInfo couponInfo = response.coupons().get(0);
        assertAll("사용 가능한 쿠폰 검증",
            () -> assertNotNull(response),
            () -> assertEquals(1, response.coupons().size()), // coupon1만 사용 가능
            () -> assertEquals(20000, response.orderAmount()),
            () -> assertEquals(2000, couponInfo.expectedDiscount()), // 20000 * 10%
            () -> assertEquals(18000, couponInfo.finalAmount()) // 20000 - 2000
        );
    }

    @Test
    @DisplayName("할인율 쿠폰의 최대 할인 금액이 적용된다")
    void 할인율_쿠폰의_최대_할인_금액이_적용된다() {
        // Given
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();

        // 10% 할인, 최대 5000원
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        couponRepository.save(coupon);

        UserCoupon userCoupon = new UserCoupon(userId, coupon.getId(), now.plusDays(30));
        userCouponRepository.save(userCoupon);

        // When - 주문금액 100,000원 (10% = 10,000원이지만 최대 5,000원)
        AvailableUserCouponListResponse response = getAvailableUserCouponsUseCase.execute(userId, 100000);

        // Then
        AvailableUserCouponListResponse.AvailableUserCouponInfo couponInfo = response.coupons().get(0);
        assertAll("최대 할인 금액 검증",
            () -> assertEquals(5000, couponInfo.expectedDiscount()), // 최대 할인 금액
            () -> assertEquals(95000, couponInfo.finalAmount()) // 100000 - 5000
        );
    }

    @Test
    @DisplayName("고정 금액 할인 쿠폰이 정상 작동한다")
    void 고정_금액_할인_쿠폰이_정상_작동한다() {
        // Given
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();

        Coupon coupon = new Coupon("5000원 할인", CouponType.AMOUNT, 5000, null, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        couponRepository.save(coupon);

        UserCoupon userCoupon = new UserCoupon(userId, coupon.getId(), now.plusDays(30));
        userCouponRepository.save(userCoupon);

        // When
        AvailableUserCouponListResponse response = getAvailableUserCouponsUseCase.execute(userId, 30000);

        // Then
        AvailableUserCouponListResponse.AvailableUserCouponInfo couponInfo = response.coupons().get(0);
        assertAll("고정 금액 할인 검증",
            () -> assertEquals(5000, couponInfo.expectedDiscount()),
            () -> assertEquals(25000, couponInfo.finalAmount()) // 30000 - 5000
        );
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
