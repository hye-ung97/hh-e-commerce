package org.hhplus.hhecommerce.api.dto.coupon;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "쿠폰 목록 응답")
public record CouponListResponse(
        @Schema(description = "쿠폰 목록")
        List<CouponInfo> coupons,

        @Schema(description = "총 쿠폰 수", example = "3")
        Integer totalCount
) {

    @Schema(description = "쿠폰 정보")
    public record CouponInfo(
            @Schema(description = "쿠폰 ID", example = "1")
            Long id,

            @Schema(description = "쿠폰명", example = "신규가입 10% 할인")
            String name,

            @Schema(description = "할인 타입", example = "RATE", allowableValues = {"RATE", "AMOUNT"})
            String discountType,

            @Schema(description = "할인 값", example = "10")
            Integer discountValue,

            @Schema(description = "최대 할인 금액 (원)", example = "10000")
            Integer maxDiscountAmount,

            @Schema(description = "최소 주문 금액 (원)", example = "100000")
            Integer minOrderAmount,

            @Schema(description = "잔여 수량", example = "45")
            Integer remainingQuantity,

            @Schema(description = "발급 시작일", example = "2025-10-01T00:00:00")
            LocalDateTime startAt,

            @Schema(description = "발급 종료일", example = "2025-12-31T23:59:59")
            LocalDateTime endAt
    ) {
    }
}
