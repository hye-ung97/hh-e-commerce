package org.hhplus.hhecommerce.application.coupon;

import org.hhplus.hhecommerce.api.dto.coupon.CouponListResponse;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.CouponType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class GetAvailableCouponsUseCaseTest {

    private GetAvailableCouponsUseCase getAvailableCouponsUseCase;
    private TestCouponRepository couponRepository;

    @BeforeEach
    void setUp() {
        couponRepository = new TestCouponRepository();
        getAvailableCouponsUseCase = new GetAvailableCouponsUseCase(couponRepository);
    }

    @Test
    @DisplayName("정상적으로 발급 가능한 쿠폰 목록을 조회할 수 있다")
    void 정상적으로_발급_가능한_쿠폰_목록을_조회할_수_있다() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        couponRepository.save(coupon);

        // When
        CouponListResponse response = getAvailableCouponsUseCase.execute(0, 10);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.coupons()).hasSize(1);
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.coupons().get(0).name()).isEqualTo("10% 할인");
    }

    @Test
    @DisplayName("기간이 지난 쿠폰은 조회되지 않는다")
    void 기간이_지난_쿠폰은_조회되지_않는다() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Coupon expiredCoupon = new Coupon("만료된 쿠폰", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(30), now.minusDays(1));
        couponRepository.save(expiredCoupon);

        // When
        CouponListResponse response = getAvailableCouponsUseCase.execute(0, 10);

        // Then
        assertThat(response.coupons()).hasSize(0);
        assertThat(response.totalCount()).isEqualTo(0);
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
}
