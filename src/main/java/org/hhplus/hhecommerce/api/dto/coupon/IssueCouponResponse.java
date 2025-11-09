package org.hhplus.hhecommerce.api.dto.coupon;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "쿠폰 발급 응답")
public record IssueCouponResponse(
        @Schema(description = "사용자 쿠폰 ID", example = "1")
        Long id,

        @Schema(description = "사용자 ID", example = "1")
        Long userId,

        @Schema(description = "쿠폰 ID", example = "1")
        Long couponId,

        @Schema(description = "쿠폰명", example = "신규가입 10% 할인")
        String couponName,

        @Schema(description = "할인 타입", example = "PERCENTAGE")
        String discountType,

        @Schema(description = "할인 값", example = "10")
        Integer discountValue,

        @Schema(description = "최소 주문 금액 (원)", example = "100000")
        Integer minOrderAmount,

        @Schema(description = "사용 여부", example = "false")
        Boolean isUsed,

        @Schema(description = "발급 일시", example = "2025-10-28T10:00:00")
        LocalDateTime issuedAt,

        @Schema(description = "만료 일시", example = "2025-11-28T10:00:00")
        LocalDateTime expiredAt,

        @Schema(description = "응답 메시지", example = "Coupon issued successfully")
        String message
) {
}
