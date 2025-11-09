package org.hhplus.hhecommerce.api.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "주문 목록 응답")
public record OrderListResponse(
        @Schema(description = "주문 목록")
        List<OrderSummary> orders,

        @Schema(description = "현재 페이지", example = "0")
        Integer page,

        @Schema(description = "페이지 크기", example = "10")
        Integer size,

        @Schema(description = "전체 주문 수", example = "5")
        Integer total
) {

    @Schema(description = "주문 요약 정보")
    public record OrderSummary(
            @Schema(description = "주문 ID", example = "1")
            Long id,

            @Schema(description = "주문 상태", example = "COMPLETED")
            String status,

            @Schema(description = "최종 결제 금액 (원)", example = "1350000")
            Integer finalAmount,

            @Schema(description = "총 항목 수", example = "2")
            Integer itemCount,

            @Schema(description = "생성 일시", example = "2025-10-28T10:00:00")
            LocalDateTime createdAt
    ) {
    }
}
