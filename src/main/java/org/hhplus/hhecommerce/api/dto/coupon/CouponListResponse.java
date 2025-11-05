package org.hhplus.hhecommerce.api.dto.coupon;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "쿠폰 목록 응답")
public class CouponListResponse {

    @Schema(description = "쿠폰 목록")
    private List<CouponInfo> coupons;

    @Schema(description = "총 쿠폰 수", example = "3")
    private Integer totalCount;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "쿠폰 정보")
    public static class CouponInfo {

        @Schema(description = "쿠폰 ID", example = "1")
        private Long id;

        @Schema(description = "쿠폰명", example = "신규가입 10% 할인")
        private String name;

        @Schema(description = "할인 타입", example = "RATE", allowableValues = {"RATE", "AMOUNT"})
        private String discountType;

        @Schema(description = "할인 값", example = "10")
        private Integer discountValue;

        @Schema(description = "최대 할인 금액 (원)", example = "10000")
        private Integer maxDiscountAmount;

        @Schema(description = "최소 주문 금액 (원)", example = "100000")
        private Integer minOrderAmount;

        @Schema(description = "잔여 수량", example = "45")
        private Integer remainingQuantity;

        @Schema(description = "발급 시작일", example = "2025-10-01T00:00:00")
        private LocalDateTime startAt;

        @Schema(description = "발급 종료일", example = "2025-12-31T23:59:59")
        private LocalDateTime endAt;
    }
}
