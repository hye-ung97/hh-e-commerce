package org.hhplus.hhecommerce.application.coupon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueManager;
import org.hhplus.hhecommerce.domain.coupon.FailedCouponRollback;
import org.hhplus.hhecommerce.domain.coupon.FailedCouponRollbackRepository;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponRollbackRecoveryService {

    private static final int MAX_RETRY_COUNT = 3;
    private static final int BATCH_SIZE = 100;
    private static final String RECOVERY_EXECUTOR = "BATCH_RECOVERY";

    private final FailedCouponRollbackRepository failedCouponRollbackRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponIssueManager couponIssueManager;

    @Scheduled(fixedDelay = 300000) // 5분
    public void recoverFailedRollbacks() {
        log.debug("롤백 실패 복구 작업 시작");

        List<FailedCouponRollback> pendingRollbacks = failedCouponRollbackRepository
                .findPendingForRetry(MAX_RETRY_COUNT, PageRequest.of(0, BATCH_SIZE));

        if (pendingRollbacks.isEmpty()) {
            log.debug("복구할 롤백 실패 기록 없음");
            return;
        }

        log.info("롤백 실패 복구 시작 - 대상: {}건", pendingRollbacks.size());

        int resolved = 0;
        int ignored = 0;
        int failed = 0;

        for (FailedCouponRollback rollback : pendingRollbacks) {
            try {
                RecoveryResult result = processRecovery(rollback);
                switch (result) {
                    case RESOLVED -> resolved++;
                    case IGNORED -> ignored++;
                    case RETRY_LATER -> failed++;
                }
            } catch (Exception e) {
                log.error("롤백 복구 처리 중 오류 - id: {}, error: {}",
                        rollback.getId(), e.getMessage(), e);
                failed++;
            }
        }

        log.info("롤백 실패 복구 완료 - 복구: {}건, 무시: {}건, 재시도필요: {}건",
                resolved, ignored, failed);
    }

   
    @Transactional
    public RecoveryResult processRecovery(FailedCouponRollback rollback) {
        Long couponId = rollback.getCouponId();
        Long userId = rollback.getUserId();

        boolean userCouponExists = userCouponRepository.existsByUserIdAndCouponId(userId, couponId);

        if (userCouponExists) {
            rollback.ignore(RECOVERY_EXECUTOR);
            failedCouponRollbackRepository.save(rollback);
            log.info("롤백 무시 처리 - DB에 정상 발급됨. couponId: {}, userId: {}", couponId, userId);
            return RecoveryResult.IGNORED;
        }

        try {
            couponIssueManager.rollback(couponId, userId);
            rollback.resolve(RECOVERY_EXECUTOR);
            failedCouponRollbackRepository.save(rollback);
            log.info("롤백 복구 성공 - couponId: {}, userId: {}", couponId, userId);
            return RecoveryResult.RESOLVED;
        } catch (Exception e) {
            rollback.incrementRetryCount();
            failedCouponRollbackRepository.save(rollback);

            if (!rollback.canRetry(MAX_RETRY_COUNT)) {
                log.error("롤백 복구 최대 재시도 초과 - 수동 처리 필요. couponId: {}, userId: {}",
                        couponId, userId);
            } else {
                log.warn("롤백 복구 실패 - 재시도 예정. couponId: {}, userId: {}, retryCount: {}",
                        couponId, userId, rollback.getRetryCount());
            }
            return RecoveryResult.RETRY_LATER;
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOldRecords() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        int deleted = failedCouponRollbackRepository.deleteResolvedBefore(threshold);
        if (deleted > 0) {
            log.info("오래된 롤백 실패 기록 삭제 - {}건", deleted);
        }
    }

    public enum RecoveryResult {
        RESOLVED,
        IGNORED,
        RETRY_LATER
    }
}
