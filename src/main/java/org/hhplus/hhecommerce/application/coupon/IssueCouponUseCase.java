package org.hhplus.hhecommerce.application.coupon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.api.dto.coupon.IssueCouponResponse;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueManager;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueResult;
import org.hhplus.hhecommerce.domain.coupon.CouponIssuedEvent;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.UserCoupon;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponErrorCode;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class IssueCouponUseCase {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponIssueManager couponIssueManager;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public IssueCouponResponse execute(Long userId, Long couponId) {
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            log.warn("이미 발급된 쿠폰 - userId: {}, couponId: {}", userId, couponId);
            throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
        }

        CouponIssueResult result = couponIssueManager.tryIssue(couponId, userId);

        if (!result.isSuccess()) {
            throw mapToException(result);
        }

        eventPublisher.publishEvent(new CouponIssuedEvent(couponId, userId));

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

        if (couponIssueManager.shouldUpdateCouponStock()) {
            coupon.issue();
            couponRepository.save(coupon);
        }

        UserCoupon userCoupon = new UserCoupon(userId, couponId, LocalDateTime.now().plusDays(30));
        userCouponRepository.save(userCoupon);

        log.info("UserCoupon created for user {} and coupon {}", userId, couponId);

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
    }

    private CouponException mapToException(CouponIssueResult result) {
        return switch (result) {
            case ALREADY_ISSUED -> new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
            case OUT_OF_STOCK -> new CouponException(CouponErrorCode.COUPON_OUT_OF_STOCK);
            case COUPON_NOT_FOUND -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND);
            case NOT_AVAILABLE -> new CouponException(CouponErrorCode.COUPON_NOT_AVAILABLE);
            case LOCK_ACQUISITION_FAILED -> new CouponException(CouponErrorCode.COUPON_ISSUE_TIMEOUT);
            case PENDING_IN_PROGRESS -> new CouponException(CouponErrorCode.COUPON_ISSUE_TIMEOUT);
            case ISSUE_FAILED -> new CouponException(CouponErrorCode.COUPON_ISSUE_FAILED);
            default -> new CouponException(CouponErrorCode.COUPON_ISSUE_FAILED);
        };
    }
}
