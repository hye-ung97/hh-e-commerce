package org.hhplus.hhecommerce.application.coupon;

import org.hhplus.hhecommerce.api.dto.coupon.UserCouponListResponse;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserCouponsUseCaseTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @InjectMocks
    private GetUserCouponsUseCase getUserCouponsUseCase;

    @Test
    @DisplayName("사용자의 쿠폰 목록을 조회할 수 있다")
    void 사용자의_쿠폰_목록을_조회할_수_있다() {
        // Given
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();

        Coupon coupon1 = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        coupon1.setId(1L);

        Coupon coupon2 = new Coupon("5000원 할인", CouponType.AMOUNT, 5000, null, 30000, 100,
                now.minusDays(1), now.plusDays(30));
        coupon2.setId(2L);

        UserCoupon userCoupon1 = new UserCoupon(userId, coupon1.getId(), now.plusDays(30));
        userCoupon1.setId(1L);

        UserCoupon userCoupon2 = new UserCoupon(userId, coupon2.getId(), now.plusDays(30));
        userCoupon2.setId(2L);

        when(userCouponRepository.findByUserId(userId)).thenReturn(List.of(userCoupon1, userCoupon2));
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon1));
        when(couponRepository.findById(2L)).thenReturn(Optional.of(coupon2));

        // When
        UserCouponListResponse response = getUserCouponsUseCase.execute(userId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.coupons()).hasSize(2);
        assertThat(response.totalCount()).isEqualTo(2);
    }
}
