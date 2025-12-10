package org.hhplus.hhecommerce.application.coupon;

import org.hhplus.hhecommerce.domain.coupon.CouponIssueManager;
import org.hhplus.hhecommerce.domain.coupon.FailedCouponRollback;
import org.hhplus.hhecommerce.domain.coupon.FailedCouponRollbackRepository;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponRollbackRecoveryService 테스트")
class CouponRollbackRecoveryServiceTest {

    @Mock
    private FailedCouponRollbackRepository failedCouponRollbackRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private CouponIssueManager couponIssueManager;

    @InjectMocks
    private CouponRollbackRecoveryService couponRollbackRecoveryService;

    private FailedCouponRollback createPendingRollback(Long couponId, Long userId) {
        return new FailedCouponRollback(couponId, userId, "DB 저장 실패", "Redis 롤백 실패");
    }

    @Nested
    @DisplayName("recoverFailedRollbacks 테스트")
    class RecoverFailedRollbacksTest {

        @Test
        @DisplayName("복구할 롤백 기록이 없으면 아무 작업도 수행하지 않는다")
        void shouldDoNothingWhenNoPendingRollbacks() {
            // given
            when(failedCouponRollbackRepository.findPendingForRetry(anyInt(), any(PageRequest.class)))
                    .thenReturn(Collections.emptyList());

            // when
            couponRollbackRecoveryService.recoverFailedRollbacks();

            // then
            verify(userCouponRepository, never()).existsByUserIdAndCouponId(anyLong(), anyLong());
            verify(couponIssueManager, never()).rollback(anyLong(), anyLong());
        }

        @Test
        @DisplayName("복구 대상이 있으면 각 롤백을 처리한다")
        void shouldProcessEachPendingRollback() {
            // given
            FailedCouponRollback rollback1 = createPendingRollback(1L, 100L);
            FailedCouponRollback rollback2 = createPendingRollback(2L, 200L);

            when(failedCouponRollbackRepository.findPendingForRetry(anyInt(), any(PageRequest.class)))
                    .thenReturn(List.of(rollback1, rollback2));
            when(userCouponRepository.existsByUserIdAndCouponId(100L, 1L)).thenReturn(false);
            when(userCouponRepository.existsByUserIdAndCouponId(200L, 2L)).thenReturn(true);

            // when
            couponRollbackRecoveryService.recoverFailedRollbacks();

            // then
            verify(couponIssueManager).rollback(1L, 100L);
            verify(couponIssueManager, never()).rollback(eq(2L), anyLong());
            verify(failedCouponRollbackRepository, times(2)).save(any(FailedCouponRollback.class));
        }
    }

    @Nested
    @DisplayName("processRecovery 테스트")
    class ProcessRecoveryTest {

        @Test
        @DisplayName("DB에 UserCoupon이 존재하면 IGNORED 처리한다")
        void shouldIgnoreWhenUserCouponExists() {
            // given
            Long couponId = 1L;
            Long userId = 100L;
            FailedCouponRollback rollback = createPendingRollback(couponId, userId);

            when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).thenReturn(true);

            // when
            CouponRollbackRecoveryService.RecoveryResult result =
                    couponRollbackRecoveryService.processRecovery(rollback);

            // then
            assertThat(result).isEqualTo(CouponRollbackRecoveryService.RecoveryResult.IGNORED);
            verify(couponIssueManager, never()).rollback(anyLong(), anyLong());
            verify(failedCouponRollbackRepository).save(rollback);
        }

        @Test
        @DisplayName("DB에 UserCoupon이 없으면 Redis 롤백을 수행하고 RESOLVED 처리한다")
        void shouldRollbackAndResolveWhenUserCouponNotExists() {
            // given
            Long couponId = 1L;
            Long userId = 100L;
            FailedCouponRollback rollback = createPendingRollback(couponId, userId);

            when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).thenReturn(false);

            // when
            CouponRollbackRecoveryService.RecoveryResult result =
                    couponRollbackRecoveryService.processRecovery(rollback);

            // then
            assertThat(result).isEqualTo(CouponRollbackRecoveryService.RecoveryResult.RESOLVED);
            verify(couponIssueManager).rollback(couponId, userId);
            verify(failedCouponRollbackRepository).save(rollback);
        }

        @Test
        @DisplayName("Redis 롤백 실패 시 재시도 횟수를 증가시키고 RETRY_LATER를 반환한다")
        void shouldIncrementRetryCountWhenRollbackFails() {
            // given
            Long couponId = 1L;
            Long userId = 100L;
            FailedCouponRollback rollback = createPendingRollback(couponId, userId);

            when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).thenReturn(false);
            doThrow(new RuntimeException("Redis 연결 실패")).when(couponIssueManager).rollback(couponId, userId);

            // when
            CouponRollbackRecoveryService.RecoveryResult result =
                    couponRollbackRecoveryService.processRecovery(rollback);

            // then
            assertThat(result).isEqualTo(CouponRollbackRecoveryService.RecoveryResult.RETRY_LATER);
            assertThat(rollback.getRetryCount()).isEqualTo(1);
            verify(failedCouponRollbackRepository).save(rollback);
        }

        @Test
        @DisplayName("최대 재시도 횟수 초과 시에도 RETRY_LATER를 반환한다")
        void shouldReturnRetryLaterWhenMaxRetryExceeded() {
            // given
            Long couponId = 1L;
            Long userId = 100L;
            FailedCouponRollback rollback = createPendingRollback(couponId, userId);

            // 이미 2번 재시도한 상태로 설정
            rollback.incrementRetryCount();
            rollback.incrementRetryCount();

            when(userCouponRepository.existsByUserIdAndCouponId(userId, couponId)).thenReturn(false);
            doThrow(new RuntimeException("Redis 연결 실패")).when(couponIssueManager).rollback(couponId, userId);

            // when
            CouponRollbackRecoveryService.RecoveryResult result =
                    couponRollbackRecoveryService.processRecovery(rollback);

            // then
            assertThat(result).isEqualTo(CouponRollbackRecoveryService.RecoveryResult.RETRY_LATER);
            assertThat(rollback.getRetryCount()).isEqualTo(3);
            assertThat(rollback.canRetry(3)).isFalse();
        }
    }

    @Nested
    @DisplayName("cleanupOldRecords 테스트")
    class CleanupOldRecordsTest {

        @Test
        @DisplayName("30일 이전의 완료된 기록을 삭제한다")
        void shouldDeleteResolvedRecordsOlderThan30Days() {
            // given
            when(failedCouponRollbackRepository.deleteResolvedBefore(any())).thenReturn(5);

            // when
            couponRollbackRecoveryService.cleanupOldRecords();

            // then
            ArgumentCaptor<java.time.LocalDateTime> captor = ArgumentCaptor.forClass(java.time.LocalDateTime.class);
            verify(failedCouponRollbackRepository).deleteResolvedBefore(captor.capture());

            java.time.LocalDateTime threshold = captor.getValue();
            assertThat(threshold).isBefore(java.time.LocalDateTime.now().minusDays(29));
        }

        @Test
        @DisplayName("삭제할 기록이 없으면 정상적으로 종료된다")
        void shouldCompleteNormallyWhenNoRecordsToDelete() {
            // given
            when(failedCouponRollbackRepository.deleteResolvedBefore(any())).thenReturn(0);

            // when & then - 예외 없이 정상 종료
            couponRollbackRecoveryService.cleanupOldRecords();

            verify(failedCouponRollbackRepository).deleteResolvedBefore(any());
        }
    }
}
