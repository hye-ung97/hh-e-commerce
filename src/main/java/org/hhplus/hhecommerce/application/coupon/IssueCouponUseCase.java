package org.hhplus.hhecommerce.application.coupon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.api.dto.coupon.IssueCouponResponse;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueManager;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueResult;
import org.hhplus.hhecommerce.domain.coupon.FailedCouponRollback;
import org.hhplus.hhecommerce.domain.coupon.FailedCouponRollbackRepository;
import org.hhplus.hhecommerce.domain.coupon.UserCoupon;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponErrorCode;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@RequiredArgsConstructor
public class IssueCouponUseCase {

    private final CouponIssueManager couponIssueManager;
    private final CouponTransactionService couponTransactionService;
    private final FailedCouponRollbackRepository failedCouponRollbackRepository;

    public IssueCouponResponse execute(Long userId, Long couponId) {
        CouponIssueResult result = couponIssueManager.tryIssue(couponId, userId);

        if (!result.isSuccess()) {
            throw mapToException(result);
        }

        try {
            CouponTransactionService.CouponSaveResult saveResult =
                    couponTransactionService.saveUserCoupon(userId, couponId);

            UserCoupon userCoupon = saveResult.userCoupon();
            Coupon coupon = saveResult.coupon();

            return new IssueCouponResponse(
                    userCoupon.getId(),
                    userId,
                    coupon.getId(),
                    coupon.getName(),
                    coupon.getDiscountType().name(),
                    coupon.getDiscountValue(),
                    coupon.getMinOrderAmount(),
                    false,
                    userCoupon.getCreatedAt(),
                    userCoupon.getExpiredAt(),
                    result.getMessage()
            );
        } catch (CouponException e) {
            log.warn("쿠폰 발급 비즈니스 실패, Redis 롤백 수행 - userId: {}, couponId: {}, reason: {}",
                    userId, couponId, e.getMessage());
            rollbackRedis(couponId, userId, e);
            throw e;
        } catch (Exception e) {
            log.error("쿠폰 DB 저장 시스템 오류, Redis 롤백 수행 - userId: {}, couponId: {}", userId, couponId, e);
            rollbackRedis(couponId, userId, e);
            throw e;
        }
    }

    private void rollbackRedis(Long couponId, Long userId, Exception originalException) {
        try {
            couponIssueManager.rollback(couponId, userId);
        } catch (Exception rollbackEx) {
            log.error("Redis 롤백 실패 - 실패 기록 저장. userId: {}, couponId: {}, " +
                      "originalError: {}, rollbackError: {}",
                    userId, couponId, originalException.getMessage(), rollbackEx.getMessage(), rollbackEx);

            saveFailedRollback(couponId, userId, originalException, rollbackEx);
        }
    }

    private void saveFailedRollback(Long couponId, Long userId,
                                     Exception originalException, Exception rollbackException) {
        try {
            FailedCouponRollback failedRollback = new FailedCouponRollback(
                    couponId,
                    userId,
                    originalException.getMessage(),
                    rollbackException.getMessage()
            );
            failedCouponRollbackRepository.save(failedRollback);
            log.info("롤백 실패 기록 저장 완료 - userId: {}, couponId: {}", userId, couponId);
        } catch (Exception saveEx) {
            log.error("롤백 실패 기록 저장 실패 - 수동 확인 필요. userId: {}, couponId: {}, error: {}",
                    userId, couponId, saveEx.getMessage(), saveEx);
        }
    }

    private CouponException mapToException(CouponIssueResult result) {
        return switch (result) {
            case ALREADY_ISSUED -> new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
            case OUT_OF_STOCK -> new CouponException(CouponErrorCode.COUPON_OUT_OF_STOCK);
            case COUPON_NOT_FOUND -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND);
            case NOT_AVAILABLE -> new CouponException(CouponErrorCode.COUPON_NOT_AVAILABLE);
            case LOCK_ACQUISITION_FAILED, PENDING_IN_PROGRESS -> new CouponException(CouponErrorCode.COUPON_ISSUE_TIMEOUT);
            default -> new CouponException(CouponErrorCode.COUPON_ISSUE_FAILED);
        };
    }
}
