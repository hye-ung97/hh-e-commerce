package org.hhplus.hhecommerce.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.product.*;
import org.hhplus.hhecommerce.application.product.GetPopularProductsUseCase;
import org.hhplus.hhecommerce.application.product.GetProductDetailUseCase;
import org.hhplus.hhecommerce.application.product.GetProductsUseCase;
import org.hhplus.hhecommerce.application.ranking.GetRealtimeRankingUseCase;
import org.hhplus.hhecommerce.domain.ranking.RankingType;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Product", description = "상품 관리 API")
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final GetProductsUseCase getProductsUseCase;
    private final GetProductDetailUseCase getProductDetailUseCase;
    private final GetPopularProductsUseCase getPopularProductsUseCase;
    private final GetRealtimeRankingUseCase getRealtimeRankingUseCase;

    @Operation(summary = "상품 목록 조회")
    @GetMapping
    public ProductListResponse getProducts(
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size
    ) {
        return getProductsUseCase.execute(page, size);
    }

    @Operation(summary = "상품 상세 조회")
    @GetMapping("/{productId}")
    public ProductDetailResponse getProductDetail(
        @Parameter(description = "상품 ID") @PathVariable Long productId
    ) {
        return getProductDetailUseCase.execute(productId);
    }

    @Operation(summary = "인기 상품 조회")
    @GetMapping("/popular")
    public PopularProductsResponse getPopularProducts() {
        return getPopularProductsUseCase.execute();
    }

    @Operation(summary = "실시간 일간 랭킹 조회")
    @GetMapping("/ranking/daily")
    public RealtimeRankingResponse getDailyRanking(
        @Parameter(description = "조회할 개수 (최대 50)", example = "10") @RequestParam(defaultValue = "10") int limit
    ) {
        return getRealtimeRankingUseCase.execute(RankingType.DAILY, limit);
    }

    @Operation(summary = "실시간 주간 랭킹 조회")
    @GetMapping("/ranking/weekly")
    public RealtimeRankingResponse getWeeklyRanking(
        @Parameter(description = "조회할 개수 (최대 50)", example = "10") @RequestParam(defaultValue = "10") int limit
    ) {
        return getRealtimeRankingUseCase.execute(RankingType.WEEKLY, limit);
    }
}
