package org.hhplus.hhecommerce.api.dto.order;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "주문 생성 응답")
public record CreateOrderResponse(
        @Schema(description = "주문 ID", example = "1")
        Long id,

        @Schema(description = "사용자 ID", example = "1")
        Long userId,

        @Schema(description = "주문 상태", example = "COMPLETED")
        String status,

        @Schema(description = "총 상품 금액 (원)", example = "1500000")
        Integer totalAmount,

        @Schema(description = "쿠폰 할인 금액 (원)", example = "150000")
        Integer discountAmount,

        @Schema(description = "최종 결제 금액 (원)", example = "1350000")
        Integer finalAmount,

        @Schema(description = "주문 항목 목록")
        List<OrderItemInfo> items,

        @Schema(description = "생성 일시", example = "2025-10-28T10:00:00")
        LocalDateTime createdAt,

        @Schema(description = "응답 메시지", example = "Order created successfully")
        String message
) {

    @Schema(description = "주문 항목 정보")
    public record OrderItemInfo(
            @Schema(description = "상품명", example = "노트북")
            String productName,

            @Schema(description = "옵션명", example = "색상: 실버")
            String optionName,

            @Schema(description = "가격 (원)", example = "1500000")
            Integer price,

            @Schema(description = "수량", example = "1")
            Integer quantity,

            @Schema(description = "합계 (원)", example = "1500000")
            Integer totalPrice
    ) {
    }
}
