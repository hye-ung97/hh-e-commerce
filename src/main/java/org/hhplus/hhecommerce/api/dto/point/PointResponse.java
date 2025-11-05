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
@Schema(description = "포인트 조회 응답")
public class PointResponse {

    @Schema(description = "포인트 ID", example = "1")
    private Long id;

    @Schema(description = "사용자 ID", example = "1")
    private Long userId;

    @Schema(description = "포인트 (원)", example = "1000000")
    private Integer amount;

    @Schema(description = "생성 일시", example = "2024-09-28T13:51:32.123")
    private LocalDateTime createdAt;

    @Schema(description = "수정 일시", example = "2024-10-27T13:51:32.123")
    private LocalDateTime updatedAt;
}
