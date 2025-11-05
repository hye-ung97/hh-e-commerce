package org.hhplus.hhecommerce.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.coupon.*;
import org.hhplus.hhecommerce.application.coupon.GetAvailableCouponsUseCase;
import org.hhplus.hhecommerce.application.coupon.GetAvailableUserCouponsUseCase;
import org.hhplus.hhecommerce.application.coupon.GetUserCouponsUseCase;
import org.hhplus.hhecommerce.application.coupon.IssueCouponUseCase;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Coupon", description = "쿠폰 관리 API")
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final GetAvailableCouponsUseCase getAvailableCouponsUseCase;
    private final IssueCouponUseCase issueCouponUseCase;
    private final GetUserCouponsUseCase getUserCouponsUseCase;
    private final GetAvailableUserCouponsUseCase getAvailableUserCouponsUseCase;

    @Operation(summary = "발급 가능한 쿠폰 목록 조회")
    @GetMapping
    public CouponListResponse getAvailableCoupons(
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size
    ) {
        return getAvailableCouponsUseCase.execute(page, size);
    }

    @Operation(summary = "쿠폰 발급")
    @PostMapping("/{couponId}/issue")
    public IssueCouponResponse issueCoupon(
        @Parameter(description = "사용자 ID") @RequestParam(defaultValue = "1") Long userId,
        @Parameter(description = "쿠폰 ID") @PathVariable Long couponId
    ) {
        return issueCouponUseCase.execute(userId, couponId);
    }

    @Operation(summary = "보유 쿠폰 조회")
    @GetMapping("/users/coupons")
    public UserCouponListResponse getUserCoupons(
        @Parameter(description = "사용자 ID") @RequestParam(defaultValue = "1") Long userId
    ) {
        return getUserCouponsUseCase.execute(userId);
    }

    @Operation(summary = "사용 가능한 쿠폰 조회")
    @GetMapping("/users/coupons/available")
    public AvailableUserCouponListResponse getAvailableUserCoupons(
        @Parameter(description = "사용자 ID") @RequestParam(defaultValue = "1") Long userId,
        @Parameter(description = "주문 금액") @RequestParam Integer orderAmount
    ) {
        return getAvailableUserCouponsUseCase.execute(userId, orderAmount);
    }
}
