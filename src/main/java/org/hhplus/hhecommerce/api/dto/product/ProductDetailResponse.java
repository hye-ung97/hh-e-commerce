package org.hhplus.hhecommerce.api.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "상품 상세 응답")
public record ProductDetailResponse(
        @Schema(description = "상품 ID", example = "1")
        Long id,

        @Schema(description = "상품명", example = "노트북")
        String name,

        @Schema(description = "카테고리", example = "전자제품")
        String category,

        @Schema(description = "상품 상태", example = "ACTIVE", allowableValues = {"ACTIVE", "INACTIVE"})
        String status,

        @Schema(description = "상품 옵션 목록")
        List<ProductOptionInfo> options
) {

    @Schema(description = "상품 옵션")
    public record ProductOptionInfo(
            @Schema(description = "옵션 ID", example = "1")
            Long id,

            @Schema(description = "옵션명", example = "색상")
            String optionName,

            @Schema(description = "옵션값", example = "실버")
            String optionValue,

            @Schema(description = "가격 (원)", example = "100000")
            Integer price,

            @Schema(description = "재고 수량", example = "5")
            Integer stock
    ) {
    }
}
