package org.hhplus.hhecommerce.api.dto.point;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "포인트 조회 응답")
public record PointResponse(
        @Schema(description = "포인트 ID", example = "1")
        Long id,

        @Schema(description = "사용자 ID", example = "1")
        Long userId,

        @Schema(description = "포인트 (원)", example = "1000000")
        Integer amount,

        @Schema(description = "생성 일시", example = "2024-09-28T13:51:32.123")
        LocalDateTime createdAt,

        @Schema(description = "수정 일시", example = "2024-10-27T13:51:32.123")
        LocalDateTime updatedAt
) {
}
