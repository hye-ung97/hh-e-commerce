package org.hhplus.hhecommerce.api.dto.product;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "실시간 랭킹 응답")
public record RealtimeRankingResponse(

        @Schema(description = "랭킹 상품 목록")
        List<RankingProduct> rankings,

        @Schema(description = "랭킹 타입 (DAILY/WEEKLY)")
        String rankingType,

        @Schema(description = "총 개수")
        int totalCount
) {

    @Schema(description = "랭킹 상품 정보")
    public record RankingProduct(

            @Schema(description = "순위", example = "1")
            long rank,

            @Schema(description = "상품 ID", example = "1")
            Long productId,

            @Schema(description = "상품명", example = "인기 상품")
            String productName,

            @Schema(description = "카테고리", example = "전자기기")
            String category,

            @Schema(description = "상품 상태", example = "ACTIVE")
            String status,

            @Schema(description = "판매 수량", example = "150")
            int salesCount
    ) {
    }
}
