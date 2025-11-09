package org.hhplus.hhecommerce.application.coupon;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.coupon.AvailableUserCouponListResponse;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.UserCoupon;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponErrorCode;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetAvailableUserCouponsUseCase {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    public AvailableUserCouponListResponse execute(Long userId, Integer orderAmount) {
        LocalDateTime now = LocalDateTime.now();

        List<UserCoupon> availableUserCoupons = userCouponRepository.findAvailableByUserId(userId, now);

        List<AvailableUserCouponListResponse.AvailableUserCouponInfo> couponInfos = availableUserCoupons.stream()
                .map(uc -> {
                    Coupon coupon = couponRepository.findById(uc.getCouponId())
                            .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
                    return new Object[]{uc, coupon};
                })
                .filter(pair -> orderAmount >= ((Coupon)pair[1]).getMinOrderAmount())
                .map(pair -> {
                    UserCoupon uc = (UserCoupon) pair[0];
                    Coupon coupon = (Coupon) pair[1];
                    int discountAmount = coupon.calculateDiscount(orderAmount);
                    int finalAmount = orderAmount - discountAmount;

                    return new AvailableUserCouponListResponse.AvailableUserCouponInfo(
                            uc.getId(),
                            uc.getUserId(),
                            coupon.getId(),
                            coupon.getName(),
                            coupon.getDiscountType().name(),
                            coupon.getDiscountValue(),
                            coupon.getMinOrderAmount(),
                            uc.getStatus().name(),
                            uc.getCreatedAt(),
                            uc.getExpiredAt(),
                            discountAmount,
                            finalAmount
                    );
                })
                .collect(Collectors.toList());

        return new AvailableUserCouponListResponse(couponInfos, couponInfos.size(), orderAmount);
    }
}
