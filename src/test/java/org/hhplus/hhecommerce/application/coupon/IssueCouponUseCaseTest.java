package org.hhplus.hhecommerce.application.coupon;

import org.hhplus.hhecommerce.api.dto.coupon.IssueCouponResponse;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueManager;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueResult;
import org.hhplus.hhecommerce.domain.coupon.CouponIssuedEvent;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.CouponType;
import org.hhplus.hhecommerce.domain.coupon.UserCoupon;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponErrorCode;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueCouponUseCaseTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private CouponIssueManager couponIssueManager;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private IssueCouponUseCase issueCouponUseCase;

    @BeforeEach
    void setUp() {
        issueCouponUseCase = new IssueCouponUseCase(
                couponRepository,
                userCouponRepository,
                couponIssueManager,
                eventPublisher
        );
    }

    @Test
    @DisplayName("정상적으로 쿠폰을 발급할 수 있다")
    void 정상적으로_쿠폰을_발급할_수_있다() {
        // Given
        Long userId = 1L;
        Long couponId = 1L;
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        coupon.setId(couponId);

        when(couponIssueManager.tryIssue(couponId, userId)).thenReturn(CouponIssueResult.SUCCESS);
        when(couponRepository.findById(couponId)).thenReturn(Optional.of(coupon));
        when(userCouponRepository.save(any(UserCoupon.class))).thenAnswer(invocation -> {
            UserCoupon userCoupon = invocation.getArgument(0);
            userCoupon.setId(1L);
            return userCoupon;
        });

        // When
        IssueCouponResponse response = issueCouponUseCase.execute(userId, couponId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.couponId()).isEqualTo(couponId);
        assertThat(response.couponName()).isEqualTo("10% 할인");
        assertThat(response.message()).contains("발급");

        verify(couponIssueManager).tryIssue(couponId, userId);
        verify(userCouponRepository).save(any(UserCoupon.class));

        ArgumentCaptor<CouponIssuedEvent> eventCaptor = ArgumentCaptor.forClass(CouponIssuedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        CouponIssuedEvent event = eventCaptor.getValue();
        assertThat(event.couponId()).isEqualTo(couponId);
        assertThat(event.userId()).isEqualTo(userId);
    }

    @Test
    @DisplayName("이미 발급받은 쿠폰은 다시 발급받을 수 없다")
    void 이미_발급받은_쿠폰은_다시_발급받을_수_없다() {
        // Given
        Long userId = 1L;
        Long couponId = 1L;

        when(couponIssueManager.tryIssue(couponId, userId)).thenReturn(CouponIssueResult.ALREADY_ISSUED);

        // When & Then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.class)
                .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_ALREADY_ISSUED);

        verify(userCouponRepository, never()).save(any(UserCoupon.class));
    }

    @Test
    @DisplayName("수량이 소진된 쿠폰은 발급할 수 없다")
    void 수량이_소진된_쿠폰은_발급할_수_없다() {
        // Given
        Long userId = 1L;
        Long couponId = 1L;

        when(couponIssueManager.tryIssue(couponId, userId)).thenReturn(CouponIssueResult.OUT_OF_STOCK);

        // When & Then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.class)
                .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_OUT_OF_STOCK);

        verify(userCouponRepository, never()).save(any(UserCoupon.class));
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰은 발급할 수 없다")
    void 존재하지_않는_쿠폰은_발급할_수_없다() {
        // Given
        Long userId = 1L;
        Long couponId = 999L;

        when(couponIssueManager.tryIssue(couponId, userId)).thenReturn(CouponIssueResult.COUPON_NOT_FOUND);

        // When & Then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.class)
                .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_NOT_FOUND);

        verify(userCouponRepository, never()).save(any(UserCoupon.class));
    }

    @Test
    @DisplayName("락 획득에 실패하면 예외가 발생한다")
    void 락_획득_실패_시_예외_발생() {
        // Given
        Long userId = 1L;
        Long couponId = 1L;

        when(couponIssueManager.tryIssue(couponId, userId)).thenReturn(CouponIssueResult.LOCK_ACQUISITION_FAILED);

        // When & Then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.class)
                .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_ISSUE_TIMEOUT);

        verify(userCouponRepository, never()).save(any(UserCoupon.class));
    }

    @Test
    @DisplayName("발급 기간이 아닌 쿠폰은 발급할 수 없다")
    void 발급_기간이_아닌_쿠폰은_발급할_수_없다() {
        // Given
        Long userId = 1L;
        Long couponId = 1L;

        when(couponIssueManager.tryIssue(couponId, userId)).thenReturn(CouponIssueResult.NOT_AVAILABLE);

        // When & Then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(CouponException.class)
                .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_NOT_AVAILABLE);

        verify(userCouponRepository, never()).save(any(UserCoupon.class));
    }
}
