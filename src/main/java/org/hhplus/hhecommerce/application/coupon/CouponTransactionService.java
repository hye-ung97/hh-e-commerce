package org.hhplus.hhecommerce.application.coupon;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueManager;
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
public class CouponTransactionService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponIssueManager couponIssueManager;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CouponSaveResult saveUserCoupon(Long userId, Long couponId) {
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            log.warn("DB에서 중복 발급 감지 - userId: {}, couponId: {}", userId, couponId);
            throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
        }

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

        if (couponIssueManager.shouldUpdateCouponStock()) {
            coupon.issue();
            couponRepository.save(coupon);
        }

        UserCoupon userCoupon = new UserCoupon(userId, couponId, LocalDateTime.now().plusDays(30));
        userCouponRepository.save(userCoupon);

        eventPublisher.publishEvent(new CouponIssuedEvent(couponId, userId));

        log.info("UserCoupon created for user {} and coupon {}", userId, couponId);

        return new CouponSaveResult(userCoupon, coupon);
    }

    public record CouponSaveResult(UserCoupon userCoupon, Coupon coupon) {}
}
