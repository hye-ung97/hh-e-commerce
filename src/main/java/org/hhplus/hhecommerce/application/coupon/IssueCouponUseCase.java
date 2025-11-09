package org.hhplus.hhecommerce.application.coupon;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.coupon.IssueCouponResponse;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.UserCoupon;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponErrorCode;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class IssueCouponUseCase {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    private final ConcurrentHashMap<Long, Object> couponLocks = new ConcurrentHashMap<>();

    public IssueCouponResponse execute(Long userId, Long couponId) {
        Object lock = couponLocks.computeIfAbsent(couponId, k -> new Object());

        synchronized (lock) {
            Coupon coupon = couponRepository.findById(couponId)
                    .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

            if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
                throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
            }

            coupon.issue();
            couponRepository.save(coupon);

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
        }
    }
}
