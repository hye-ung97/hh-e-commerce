package org.hhplus.hhecommerce.domain.coupon;

import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

class CouponTest {

    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        now = LocalDateTime.now();
    }

    @Test
    @DisplayName("쿠폰을 생성할 수 있다")
    void 쿠폰을_생성할_수_있다() {
        // When
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(1), now.plusDays(30));

        // Then
        assertThat(coupon.getName()).isEqualTo("10% 할인");
        assertThat(coupon.getDiscountType()).isEqualTo(CouponType.RATE);
        assertThat(coupon.getDiscountValue()).isEqualTo(10);
        assertThat(coupon.getMaxDiscountAmount()).isEqualTo(5000);
        assertThat(coupon.getMinOrderAmount()).isEqualTo(10000);
        assertThat(coupon.getTotalQuantity()).isEqualTo(100);
        assertThat(coupon.getIssuedQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("발급 가능한 쿠폰인지 확인할 수 있다")
    void 발급_가능한_쿠폰인지_확인할_수_있다() {
        // Given
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(1), now.plusDays(30));

        // When & Then
        assertThat(coupon.canIssue()).isTrue();
    }

    @Test
    @DisplayName("기간이 지난 쿠폰은 발급할 수 없다")
    void 기간이_지난_쿠폰은_발급할_수_없다() {
        // Given
        Coupon coupon = new Coupon("만료된 쿠폰", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(30), now.minusDays(1));

        // When & Then
        assertThat(coupon.canIssue()).isFalse();
    }

    @Test
    @DisplayName("아직 시작하지 않은 쿠폰은 발급할 수 없다")
    void 아직_시작하지_않은_쿠폰은_발급할_수_없다() {
        // Given
        Coupon coupon = new Coupon("예정된 쿠폰", CouponType.RATE, 10, 5000, 10000, 100,
                now.plusDays(1), now.plusDays(30));

        // When & Then
        assertThat(coupon.canIssue()).isFalse();
    }

    @Test
    @DisplayName("정상적으로 쿠폰을 발급할 수 있다")
    void 정상적으로_쿠폰을_발급할_수_있다() {
        // Given
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(1), now.plusDays(30));

        // When
        coupon.issue();

        // Then
        assertThat(coupon.getIssuedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("쿠폰을 여러 번 발급할 수 있다")
    void 쿠폰을_여러_번_발급할_수_있다() {
        // Given
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 10,
                now.minusDays(1), now.plusDays(30));

        // When
        for (int i = 0; i < 5; i++) {
            coupon.issue();
        }

        // Then
        assertThat(coupon.getIssuedQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("수량이 소진된 쿠폰은 발급할 수 없다")
    void 수량이_소진된_쿠폰은_발급할_수_없다() {
        // Given
        Coupon coupon = new Coupon("한정 쿠폰", CouponType.RATE, 10, 5000, 10000, 1,
                now.minusDays(1), now.plusDays(30));

        coupon.issue();

        // When & Then
        assertThatThrownBy(() -> coupon.issue())
            .isInstanceOf(CouponException.class);

        assertThat(coupon.getIssuedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("할인율 쿠폰의 할인 금액을 계산할 수 있다")
    void 할인율_쿠폰의_할인_금액을_계산할_수_있다() {
        // Given
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, null, 10000, 100,
                now.minusDays(1), now.plusDays(30));

        // When
        int discount = coupon.calculateDiscount(50000);

        // Then
        assertThat(discount).isEqualTo(5000);
    }

    @Test
    @DisplayName("할인율 쿠폰의 최대 할인 금액이 적용된다")
    void 할인율_쿠폰의_최대_할인_금액이_적용된다() {
        // Given
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(1), now.plusDays(30));

        // When
        int discount = coupon.calculateDiscount(100000);

        // Then
        assertThat(discount).isEqualTo(5000);
    }

    @Test
    @DisplayName("고정 금액 쿠폰의 할인 금액을 계산할 수 있다")
    void 고정_금액_쿠폰의_할인_금액을_계산할_수_있다() {
        // Given
        Coupon coupon = new Coupon("5000원 할인", CouponType.AMOUNT, 5000, null, 30000, 100,
                now.minusDays(1), now.plusDays(30));

        // When
        int discount1 = coupon.calculateDiscount(50000);
        int discount2 = coupon.calculateDiscount(100000);

        // Then
        assertThat(discount1).isEqualTo(5000);
        assertThat(discount2).isEqualTo(5000);
    }

    @Test
    @DisplayName("할인율이 0%인 쿠폰도 생성할 수 있다")
    void 할인율이_0퍼센트인_쿠폰도_생성할_수_있다() {
        // Given
        Coupon coupon = new Coupon("무료 배송", CouponType.RATE, 0, null, 10000, 100,
                now.minusDays(1), now.plusDays(30));

        // When
        int discount = coupon.calculateDiscount(50000);

        // Then
        assertThat(discount).isEqualTo(0);
    }
}
