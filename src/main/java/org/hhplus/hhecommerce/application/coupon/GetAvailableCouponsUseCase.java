package org.hhplus.hhecommerce.application.coupon;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.coupon.CouponListResponse;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetAvailableCouponsUseCase {

    private final CouponRepository couponRepository;

    public CouponListResponse execute(int page, int size) {
        LocalDateTime now = LocalDateTime.now();

        List<Coupon> availableCoupons = couponRepository.findAvailableCoupons(now, PageRequest.of(page, size));
        int totalCount = couponRepository.countAvailableCoupons(now);

        List<CouponListResponse.CouponInfo> couponInfos = availableCoupons.stream()
                .map(coupon -> new CouponListResponse.CouponInfo(
                        coupon.getId(),
                        coupon.getName(),
                        coupon.getDiscountType().name(),
                        coupon.getDiscountValue(),
                        coupon.getMaxDiscountAmount(),
                        coupon.getMinOrderAmount(),
                        coupon.getTotalQuantity() - coupon.getIssuedQuantity(),
                        coupon.getStartAt(),
                        coupon.getEndAt()
                ))
                .collect(Collectors.toList());

        return new CouponListResponse(couponInfos, totalCount);
    }
}
