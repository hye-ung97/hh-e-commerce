package org.hhplus.hhecommerce.domain.coupon;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class UserCouponTest {

    private UserCoupon userCoupon;
    private LocalDateTime expiredAt;

    @BeforeEach
    void setUp() {
        expiredAt = LocalDateTime.now().plusDays(30);
        userCoupon = new UserCoupon(1L, 100L, expiredAt);
    }

    @Test
    @DisplayName("사용자 쿠폰을 생성할 수 있다")
    void 사용자_쿠폰을_생성할_수_있다() {
        // When
        UserCoupon newUserCoupon = new UserCoupon(1L, 100L, expiredAt);

        // Then
        assertThat(newUserCoupon.getUserId()).isEqualTo(1L);
        assertThat(newUserCoupon.getCouponId()).isEqualTo(100L);
        assertThat(newUserCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
        assertThat(newUserCoupon.getExpiredAt()).isEqualTo(expiredAt);
        assertThat(newUserCoupon.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("쿠폰을 생성하면 초기 상태는 AVAILABLE이다")
    void 쿠폰을_생성하면_초기_상태는_AVAILABLE이다() {
        // Then
        assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.AVAILABLE);
    }

    @Test
    @DisplayName("정상적으로 쿠폰을 사용할 수 있다")
    void 정상적으로_쿠폰을_사용할_수_있다() {
        // When
        userCoupon.use();

        // Then
        assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.USED);
        assertThat(userCoupon.getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("이미 사용한 쿠폰은 다시 사용할 수 없다")
    void 이미_사용한_쿠폰은_다시_사용할_수_없다() {
        // Given
        userCoupon.use();

        // When & Then
        assertThatThrownBy(() -> userCoupon.use())
            .isInstanceOf(IllegalStateException.class);

        assertThat(userCoupon.getStatus()).isEqualTo(CouponStatus.USED);
    }

    @Test
    @DisplayName("쿠폰 사용 시 사용 시간이 기록된다")
    void 쿠폰_사용_시_사용_시간이_기록된다() {
        // Given
        LocalDateTime beforeUse = LocalDateTime.now();

        // When
        userCoupon.use();

        // Then
        assertThat(userCoupon.getUsedAt()).isNotNull();
        assertThat(userCoupon.getUsedAt()).isAfterOrEqualTo(beforeUse);
    }
}
