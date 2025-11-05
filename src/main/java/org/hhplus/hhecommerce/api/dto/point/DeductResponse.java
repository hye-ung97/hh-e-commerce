package org.hhplus.hhecommerce.api.dto.point;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "포인트 차감 응답")
public class DeductResponse {

    @Schema(description = "포인트 ID", example = "1")
    private Long id;

    @Schema(description = "사용자 ID", example = "1")
    private Long userId;

    @Schema(description = "차감 후 포인트 (원)", example = "850000")
    private Integer amount;

    @Schema(description = "생성 일시", example = "2024-09-28T13:51:32.123")
    private LocalDateTime createdAt;

    @Schema(description = "수정 일시", example = "2025-10-28T14:35:00.000")
    private LocalDateTime updatedAt;

    @Schema(description = "차감된 금액 (원)", example = "150000")
    private Integer deductedAmount;

    @Schema(description = "응답 메시지", example = "Point deducted successfully")
    private String message;
}
