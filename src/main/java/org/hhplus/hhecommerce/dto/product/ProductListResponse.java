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
@Schema(description = "상품 목록 응답")
public class ProductListResponse {

    @Schema(description = "상품 목록")
    private List<ProductSummary> products;

    @Schema(description = "현재 페이지", example = "0")
    private Integer page;

    @Schema(description = "페이지 크기", example = "10")
    private Integer size;

    @Schema(description = "전체 상품 수", example = "5")
    private Integer total;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "상품 요약 정보")
    public static class ProductSummary {

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
    }
}
