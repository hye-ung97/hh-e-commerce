package org.hhplus.hhecommerce.dto.cart;

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
@Schema(description = "장바구니 목록 응답")
public class CartListResponse {

    @Schema(description = "장바구니 항목 목록")
    private List<CartItem> items;

    @Schema(description = "총 항목 수", example = "3")
    private Integer totalCount;

    @Schema(description = "총 금액 (원)", example = "1700000")
    private Integer totalAmount;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "장바구니 항목")
    public static class CartItem {

        @Schema(description = "장바구니 ID", example = "1")
        private Long id;

        @Schema(description = "사용자 ID", example = "1")
        private Long userId;

        @Schema(description = "상품 옵션 ID", example = "1")
        private Long productOptionId;

        @Schema(description = "상품명", example = "노트북")
        private String productName;

        @Schema(description = "옵션명", example = "색상: 실버")
        private String optionName;

        @Schema(description = "가격 (원)", example = "1500000")
        private Integer price;

        @Schema(description = "수량", example = "1")
        private Integer quantity;

        @Schema(description = "합계 금액 (원)", example = "1500000")
        private Integer totalPrice;

        @Schema(description = "생성 일시", example = "2025-10-28T10:00:00")
        private LocalDateTime createdAt;

        @Schema(description = "수정 일시", example = "2025-10-28T10:00:00")
        private LocalDateTime updatedAt;
    }
}
