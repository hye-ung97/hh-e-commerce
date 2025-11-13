package org.hhplus.hhecommerce.application.coupon;

import org.hhplus.hhecommerce.api.dto.coupon.AvailableUserCouponListResponse;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponType;
import org.hhplus.hhecommerce.domain.coupon.UserCoupon;
import org.hhplus.hhecommerce.infrastructure.repository.coupon.CouponRepository;
import org.hhplus.hhecommerce.infrastructure.repository.coupon.UserCouponRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAvailableUserCouponsUseCaseTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @InjectMocks
    private GetAvailableUserCouponsUseCase getAvailableUserCouponsUseCase;

    @Test
    @DisplayName("주문 금액에 사용 가능한 쿠폰 목록을 조회할 수 있다")
    void 주문_금액에_사용_가능한_쿠폰_목록을_조회할_수_있다() {
        // Given
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();

        // 최소 주문금액 10,000원
        Coupon coupon1 = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        coupon1.setId(1L);

        // 최소 주문금액 30,000원
        Coupon coupon2 = new Coupon("5000원 할인", CouponType.AMOUNT, 5000, null, 30000, 100,
                now.minusDays(1), now.plusDays(30));
        coupon2.setId(2L);

        UserCoupon userCoupon1 = new UserCoupon(userId, coupon1.getId(), now.plusDays(30));
        userCoupon1.setId(1L);

        UserCoupon userCoupon2 = new UserCoupon(userId, coupon2.getId(), now.plusDays(30));
        userCoupon2.setId(2L);

        when(userCouponRepository.findAvailableByUserId(anyLong(), any(LocalDateTime.class)))
            .thenReturn(List.of(userCoupon1, userCoupon2));
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon1));
        when(couponRepository.findById(2L)).thenReturn(Optional.of(coupon2));

        // When - 주문금액 20,000원
        AvailableUserCouponListResponse response = getAvailableUserCouponsUseCase.execute(userId, 20000);

        // Then
        AvailableUserCouponListResponse.AvailableUserCouponInfo couponInfo = response.coupons().get(0);
        assertThat(response).isNotNull();
        assertThat(response.coupons()).hasSize(1);
        assertThat(response.orderAmount()).isEqualTo(20000);
        assertThat(couponInfo.expectedDiscount()).isEqualTo(2000);
        assertThat(couponInfo.finalAmount()).isEqualTo(18000);
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
        coupon.setId(1L);

        UserCoupon userCoupon = new UserCoupon(userId, coupon.getId(), now.plusDays(30));
        userCoupon.setId(1L);

        when(userCouponRepository.findAvailableByUserId(anyLong(), any(LocalDateTime.class)))
            .thenReturn(List.of(userCoupon));
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));

        // When - 주문금액 100,000원 (10% = 10,000원이지만 최대 5,000원)
        AvailableUserCouponListResponse response = getAvailableUserCouponsUseCase.execute(userId, 100000);

        // Then
        AvailableUserCouponListResponse.AvailableUserCouponInfo couponInfo = response.coupons().get(0);
        assertThat(couponInfo.expectedDiscount()).isEqualTo(5000);
        assertThat(couponInfo.finalAmount()).isEqualTo(95000);
    }

    @Test
    @DisplayName("고정 금액 할인 쿠폰이 정상 작동한다")
    void 고정_금액_할인_쿠폰이_정상_작동한다() {
        // Given
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();

        Coupon coupon = new Coupon("5000원 할인", CouponType.AMOUNT, 5000, null, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        coupon.setId(1L);

        UserCoupon userCoupon = new UserCoupon(userId, coupon.getId(), now.plusDays(30));
        userCoupon.setId(1L);

        when(userCouponRepository.findAvailableByUserId(anyLong(), any(LocalDateTime.class)))
            .thenReturn(List.of(userCoupon));
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));

        // When
        AvailableUserCouponListResponse response = getAvailableUserCouponsUseCase.execute(userId, 30000);

        // Then
        AvailableUserCouponListResponse.AvailableUserCouponInfo couponInfo = response.coupons().get(0);
        assertThat(couponInfo.expectedDiscount()).isEqualTo(5000);
        assertThat(couponInfo.finalAmount()).isEqualTo(25000);
    }
}
