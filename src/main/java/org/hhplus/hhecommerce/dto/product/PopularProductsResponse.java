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
@Schema(description = "인기 상품 목록 응답")
public class PopularProductsResponse {

    @Schema(description = "인기 상품 목록")
    private List<PopularProduct> products;

    @Schema(description = "총 상품 수", example = "5")
    private Integer totalCount;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Schema(description = "인기 상품 정보")
    public static class PopularProduct {

        @Schema(description = "상품 ID", example = "1")
        private Long productId;

        @Schema(description = "상품명", example = "노트북")
        private String name;

        @Schema(description = "가격 (원)", example = "1500000")
        private Integer price;

        @Schema(description = "총 판매량", example = "120")
        private Integer totalSales;

        @Schema(description = "카테고리", example = "전자제품")
        private String category;

        @Schema(description = "상품 상태", example = "ACTIVE")
        private String status;
    }
}
