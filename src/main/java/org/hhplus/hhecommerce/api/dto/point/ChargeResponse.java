package org.hhplus.hhecommerce.api.dto.point;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "포인트 충전 응답")
public record ChargeResponse(
        @Schema(description = "포인트 ID", example = "1")
        Long id,

        @Schema(description = "사용자 ID", example = "1")
        Long userId,

        @Schema(description = "충전 후 포인트 (원)", example = "1500000")
        Integer amount,

        @Schema(description = "생성 일시", example = "2024-09-28T13:51:32.123")
        LocalDateTime createdAt,

        @Schema(description = "수정 일시", example = "2025-10-28T14:30:00.000")
        LocalDateTime updatedAt,

        @Schema(description = "충전된 금액 (원)", example = "500000")
        Integer chargedAmount,

        @Schema(description = "응답 메시지", example = "Point charged successfully")
        String message
) {
}
