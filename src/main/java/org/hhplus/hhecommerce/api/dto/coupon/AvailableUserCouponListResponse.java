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
@Schema(description = "사용 가능한 쿠폰 목록 응답")
public class AvailableUserCouponListResponse {

    @Schema(description = "사용 가능한 쿠폰 목록")
    private List<AvailableUserCouponInfo> coupons;

    @Schema(description = "총 쿠폰 수", example = "2")
    private Integer totalCount;

    @Schema(description = "주문 금액 (원)", example = "500000")
    private Integer orderAmount;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "사용 가능한 쿠폰 정보")
    public static class AvailableUserCouponInfo {

        @Schema(description = "사용자 쿠폰 ID", example = "1")
        private Long id;

        @Schema(description = "사용자 ID", example = "1")
        private Long userId;

        @Schema(description = "쿠폰 ID", example = "1")
        private Long couponId;

        @Schema(description = "쿠폰명", example = "신규가입 10% 할인")
        private String couponName;

        @Schema(description = "할인 타입", example = "PERCENTAGE")
        private String discountType;

        @Schema(description = "할인 값", example = "10")
        private Integer discountValue;

        @Schema(description = "최소 주문 금액 (원)", example = "100000")
        private Integer minOrderAmount;

        @Schema(description = "상태", example = "AVAILABLE")
        private String status;

        @Schema(description = "발급 일시", example = "2025-10-28T10:00:00")
        private LocalDateTime issuedAt;

        @Schema(description = "만료 일시", example = "2025-12-31T23:59:59")
        private LocalDateTime expiredAt;

        @Schema(description = "예상 할인 금액 (원)", example = "50000")
        private Integer expectedDiscount;

        @Schema(description = "최종 결제 금액 (원)", example = "450000")
        private Integer finalAmount;
    }
}
