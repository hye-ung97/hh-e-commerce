package org.hhplus.hhecommerce.api.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "인기 상품 목록 응답")
public record PopularProductsResponse(
        @Schema(description = "인기 상품 목록")
        List<PopularProduct> products,

        @Schema(description = "총 상품 수", example = "5")
        Integer totalCount
) {

    @Schema(description = "인기 상품 정보")
    public record PopularProduct(
            @Schema(description = "상품 ID", example = "1")
            Long productId,

            @Schema(description = "상품명", example = "노트북")
            String name,

            @Schema(description = "가격 (원)", example = "1500000")
            Integer price,

            @Schema(description = "총 판매량", example = "120")
            Integer totalSales,

            @Schema(description = "카테고리", example = "전자제품")
            String category,

            @Schema(description = "상품 상태", example = "ACTIVE")
            String status
    ) {
    }
}
