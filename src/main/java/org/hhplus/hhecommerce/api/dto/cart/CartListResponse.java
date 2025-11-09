package org.hhplus.hhecommerce.api.dto.cart;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "장바구니 목록 응답")
public record CartListResponse(
        @Schema(description = "장바구니 항목 목록")
        List<CartItem> items,

        @Schema(description = "총 항목 수", example = "3")
        Integer totalCount,

        @Schema(description = "총 금액 (원)", example = "1700000")
        Integer totalAmount
) {

    @Schema(description = "장바구니 항목")
    public record CartItem(
            @Schema(description = "장바구니 ID", example = "1")
            Long id,

            @Schema(description = "사용자 ID", example = "1")
            Long userId,

            @Schema(description = "상품 옵션 ID", example = "1")
            Long productOptionId,

            @Schema(description = "상품명", example = "노트북")
            String productName,

            @Schema(description = "옵션명", example = "색상: 실버")
            String optionName,

            @Schema(description = "가격 (원)", example = "1500000")
            Integer price,

            @Schema(description = "수량", example = "1")
            Integer quantity,

            @Schema(description = "합계 금액 (원)", example = "1500000")
            Integer totalPrice
    ) {
    }
}
