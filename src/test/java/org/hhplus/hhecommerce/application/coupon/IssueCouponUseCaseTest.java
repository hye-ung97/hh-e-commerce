package org.hhplus.hhecommerce.application.coupon;

import org.hhplus.hhecommerce.api.dto.coupon.IssueCouponResponse;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueManager;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueResult;
import org.hhplus.hhecommerce.domain.coupon.CouponType;
import org.hhplus.hhecommerce.domain.coupon.FailedCouponRollbackRepository;
import org.hhplus.hhecommerce.domain.coupon.UserCoupon;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponErrorCode;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueCouponUseCaseTest {

    @Mock
    private CouponIssueManager couponIssueManager;

    @Mock
    private CouponTransactionService couponTransactionService;

    @Mock
    private FailedCouponRollbackRepository failedCouponRollbackRepository;

    private IssueCouponUseCase issueCouponUseCase;

    @BeforeEach
    void setUp() {
        issueCouponUseCase = new IssueCouponUseCase(
                couponIssueManager,
                couponTransactionService,
                failedCouponRollbackRepository
        );
    }

    private CouponTransactionService.CouponSaveResult createMockSaveResult(Long couponId) {
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        coupon.setId(couponId);

        UserCoupon userCoupon = new UserCoupon(1L, couponId, now.plusDays(30));
        userCoupon.setId(1L);

        return new CouponTransactionService.CouponSaveResult(userCoupon, coupon);
    }

    @Test
    @DisplayName("정상적으로 쿠폰을 발급할 수 있다")
    void 정상적으로_쿠폰을_발급할_수_있다() {
        // Given
        Long userId = 1L;
        Long couponId = 1L;
        CouponTransactionService.CouponSaveResult saveResult = createMockSaveResult(couponId);

        when(couponIssueManager.tryIssue(couponId, userId)).thenReturn(CouponIssueResult.SUCCESS);
        when(couponTransactionService.saveUserCoupon(userId, couponId)).thenReturn(saveResult);

        // When
        IssueCouponResponse response = issueCouponUseCase.execute(userId, couponId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.couponId()).isEqualTo(couponId);
        assertThat(response.couponName()).isEqualTo("10% 할인");
        assertThat(response.message()).contains("발급");

        verify(couponIssueManager).tryIssue(couponId, userId);
        verify(couponTransactionService).saveUserCoupon(userId, couponId);
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

        verify(couponTransactionService, never()).saveUserCoupon(userId, couponId);
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

        verify(couponTransactionService, never()).saveUserCoupon(userId, couponId);
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

        verify(couponTransactionService, never()).saveUserCoupon(userId, couponId);
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

        verify(couponTransactionService, never()).saveUserCoupon(userId, couponId);
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

        verify(couponTransactionService, never()).saveUserCoupon(userId, couponId);
    }

    @Test
    @DisplayName("DB 저장 실패 시 Redis 롤백이 수행된다")
    void DB_저장_실패_시_Redis_롤백_수행() {
        // Given
        Long userId = 1L;
        Long couponId = 1L;

        when(couponIssueManager.tryIssue(couponId, userId)).thenReturn(CouponIssueResult.SUCCESS);
        when(couponTransactionService.saveUserCoupon(userId, couponId))
                .thenThrow(new RuntimeException("DB 저장 실패"));

        // When & Then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB 저장 실패");

        verify(couponIssueManager).rollback(couponId, userId);
    }

    @Test
    @DisplayName("Redis 롤백 실패 시 실패 기록이 DB에 저장된다")
    void Redis_롤백_실패_시_실패_기록_저장() {
        // Given
        Long userId = 1L;
        Long couponId = 1L;

        when(couponIssueManager.tryIssue(couponId, userId)).thenReturn(CouponIssueResult.SUCCESS);
        when(couponTransactionService.saveUserCoupon(userId, couponId))
                .thenThrow(new RuntimeException("DB 저장 실패"));
        doThrow(new RuntimeException("Redis 롤백 실패"))
                .when(couponIssueManager).rollback(couponId, userId);

        // When & Then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB 저장 실패");

        // 롤백 실패 기록이 저장되었는지 확인
        verify(failedCouponRollbackRepository).save(any());
    }
}
