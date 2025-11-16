package org.hhplus.hhecommerce.application.coupon;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.coupon.IssueCouponResponse;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.UserCoupon;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponErrorCode;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class IssueCouponUseCase {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    @Transactional
    public IssueCouponResponse execute(Long userId, Long couponId) {
        Coupon coupon = couponRepository.findByIdWithLock(couponId)
                .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
        coupon.issue();

        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
        }
        couponRepository.save(coupon);

        try {
            UserCoupon userCoupon = new UserCoupon(userId, couponId, LocalDateTime.now().plusDays(30));
            userCouponRepository.save(userCoupon);

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
                    "쿠폰이 발급되었습니다"
            );
        } catch (DataIntegrityViolationException e) {
            throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
        }
    }
}
