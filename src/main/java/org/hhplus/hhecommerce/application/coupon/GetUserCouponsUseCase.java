package org.hhplus.hhecommerce.application.coupon;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.coupon.UserCouponListResponse;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.CouponStatus;
import org.hhplus.hhecommerce.domain.coupon.UserCoupon;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponErrorCode;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetUserCouponsUseCase {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;

    public UserCouponListResponse execute(Long userId) {
        List<UserCoupon> userCoupons = userCouponRepository.findByUserId(userId);

        List<UserCouponListResponse.UserCouponInfo> couponInfos = userCoupons.stream()
                .map(uc -> {
                    Coupon coupon = couponRepository.findById(uc.getCouponId())
                            .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

                    return new UserCouponListResponse.UserCouponInfo(
                            uc.getId(),
                            uc.getUserId(),
                            coupon.getId(),
                            coupon.getName(),
                            coupon.getDiscountType().name(),
                            coupon.getDiscountValue(),
                            coupon.getMinOrderAmount(),
                            uc.getStatus() == CouponStatus.USED,
                            uc.getCreatedAt()
                    );
                })
                .collect(Collectors.toList());

        return new UserCouponListResponse(couponInfos, couponInfos.size());
    }
}
