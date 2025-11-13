package org.hhplus.hhecommerce.application.coupon;

import org.hhplus.hhecommerce.api.dto.coupon.CouponListResponse;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.infrastructure.repository.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.CouponType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAvailableCouponsUseCaseTest {

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private GetAvailableCouponsUseCase getAvailableCouponsUseCase;

    @Test
    @DisplayName("정상적으로 발급 가능한 쿠폰 목록을 조회할 수 있다")
    void 정상적으로_발급_가능한_쿠폰_목록을_조회할_수_있다() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        coupon.setId(1L);

        when(couponRepository.findAvailableCoupons(any(LocalDateTime.class), any(Pageable.class)))
            .thenReturn(List.of(coupon));
        when(couponRepository.countAvailableCoupons(any(LocalDateTime.class))).thenReturn(1);

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
        when(couponRepository.findAvailableCoupons(any(LocalDateTime.class), any(Pageable.class)))
            .thenReturn(List.of());
        when(couponRepository.countAvailableCoupons(any(LocalDateTime.class))).thenReturn(0);

        // When
        CouponListResponse response = getAvailableCouponsUseCase.execute(0, 10);

        // Then
        assertThat(response.coupons()).hasSize(0);
        assertThat(response.totalCount()).isEqualTo(0);
    }
}
