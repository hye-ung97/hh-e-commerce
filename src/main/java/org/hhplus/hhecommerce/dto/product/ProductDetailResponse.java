package org.hhplus.hhecommerce.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "상품 상세 응답")
public class ProductDetailResponse {

    @Schema(description = "상품 ID", example = "1")
    private Long id;

    @Schema(description = "상품명", example = "노트북")
    private String name;

    @Schema(description = "가격 (원)", example = "1500000")
    private Integer price;

    @Schema(description = "재고 수량", example = "10")
    private Integer stock;

    @Schema(description = "카테고리", example = "전자제품")
    private String category;

    @Schema(description = "상품 상태", example = "ACTIVE", allowableValues = {"ACTIVE", "INACTIVE"})
    private String status;

    @Schema(description = "상품 옵션 목록")
    private List<ProductOption> options;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "상품 옵션")
    public static class ProductOption {

        @Schema(description = "옵션 ID", example = "1")
        private Long id;

        @Schema(description = "옵션명", example = "색상: 실버")
        private String name;

        @Schema(description = "추가 가격 (원)", example = "0")
        private Integer additionalPrice;

        @Schema(description = "재고 수량", example = "5")
        private Integer stock;
    }
}
