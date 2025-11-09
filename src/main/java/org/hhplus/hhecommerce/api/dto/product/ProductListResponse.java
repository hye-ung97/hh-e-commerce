package org.hhplus.hhecommerce.api.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "상품 목록 응답")
public record ProductListResponse(
        @Schema(description = "상품 목록")
        List<ProductSummary> products,

        @Schema(description = "현재 페이지", example = "0")
        Integer page,

        @Schema(description = "페이지 크기", example = "10")
        Integer size,

        @Schema(description = "전체 상품 수", example = "5")
        Integer total
) {

    @Schema(description = "상품 요약 정보")
    public record ProductSummary(
            @Schema(description = "상품 ID", example = "1")
            Long id,

            @Schema(description = "상품명", example = "노트북")
            String name,

            @Schema(description = "가격 (원)", example = "1500000")
            Integer price,

            @Schema(description = "재고 수량", example = "10")
            Integer stock,

            @Schema(description = "카테고리", example = "전자제품")
            String category,

            @Schema(description = "상품 상태", example = "ACTIVE", allowableValues = {"ACTIVE", "INACTIVE"})
            String status
    ) {
    }
}
