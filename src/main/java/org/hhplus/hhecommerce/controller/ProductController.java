package org.hhplus.hhecommerce.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hhplus.hhecommerce.dto.product.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@Tag(name = "Product", description = "상품 관리 API")
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Map<Long, Map<String, Object>> PRODUCTS = Map.of(
        1L, Map.of("id", 1L, "name", "노트북", "price", 1500000, "stock", 10, "category", "전자제품", "status", "ACTIVE"),
        2L, Map.of("id", 2L, "name", "키보드", "price", 120000, "stock", 50, "category", "주변기기", "status", "ACTIVE"),
        3L, Map.of("id", 3L, "name", "마우스", "price", 50000, "stock", 100, "category", "주변기기", "status", "ACTIVE"),
        4L, Map.of("id", 4L, "name", "모니터", "price", 350000, "stock", 20, "category", "전자제품", "status", "ACTIVE"),
        5L, Map.of("id", 5L, "name", "헤드셋", "price", 80000, "stock", 30, "category", "주변기기", "status", "ACTIVE")
    );

    private static final Map<Long, List<Map<String, Object>>> PRODUCT_OPTIONS = Map.of(
        1L, List.of(
            Map.of("id", 1L, "productId", 1L, "name", "색상: 실버, 용량: 256GB", "price", 1500000, "stock", 5),
            Map.of("id", 2L, "productId", 1L, "name", "색상: 실버, 용량: 512GB", "price", 1700000, "stock", 5)
        ),
        2L, List.of(
            Map.of("id", 3L, "productId", 2L, "name", "색상: 블랙", "price", 120000, "stock", 25),
            Map.of("id", 4L, "productId", 2L, "name", "색상: 화이트", "price", 120000, "stock", 25)
        ),
        3L, List.of(
            Map.of("id", 5L, "productId", 3L, "name", "색상: 블랙", "price", 50000, "stock", 50),
            Map.of("id", 6L, "productId", 3L, "name", "색상: 화이트", "price", 50000, "stock", 50)
        ),
        4L, List.of(
            Map.of("id", 7L, "productId", 4L, "name", "크기: 27인치", "price", 350000, "stock", 10),
            Map.of("id", 8L, "productId", 4L, "name", "크기: 32인치", "price", 450000, "stock", 10)
        ),
        5L, List.of(
            Map.of("id", 9L, "productId", 5L, "name", "색상: 블랙", "price", 80000, "stock", 15),
            Map.of("id", 10L, "productId", 5L, "name", "색상: 화이트", "price", 80000, "stock", 15)
        )
    );

    private static final List<Map<String, Object>> POPULAR_PRODUCTS = List.of(
        Map.of("productId", 1L, "name", "노트북", "price", 1500000, "totalSales", 150),
        Map.of("productId", 3L, "name", "마우스", "price", 50000, "totalSales", 120),
        Map.of("productId", 2L, "name", "키보드", "price", 120000, "totalSales", 100),
        Map.of("productId", 4L, "name", "모니터", "price", 350000, "totalSales", 80),
        Map.of("productId", 5L, "name", "헤드셋", "price", 80000, "totalSales", 60)
    );

    @Operation(summary = "상품 목록 조회", description = "상품 목록을 페이지 단위로 조회합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ProductListResponse.class)
            )
        )
    })
    @GetMapping
    public ProductListResponse getProducts(
        @Parameter(description = "상품 상태 (ACTIVE, INACTIVE)", example = "ACTIVE")
        @RequestParam(required = false) String status,
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
        @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "페이지 크기", example = "10")
        @RequestParam(defaultValue = "10") int size
    ) {
        List<Map<String, Object>> list = new ArrayList<>(PRODUCTS.values());

        if (status != null) {
            list = list.stream()
                .filter(p -> status.equals(p.get("status")))
                .toList();
        }

        int start = page * size;
        int end = Math.min(start + size, list.size());
        List<Map<String, Object>> pagedList = start < list.size() ?
            list.subList(start, end) : Collections.emptyList();

        List<ProductListResponse.ProductSummary> productSummaries = pagedList.stream()
            .map(p -> new ProductListResponse.ProductSummary(
                (Long) p.get("id"),
                (String) p.get("name"),
                (Integer) p.get("price"),
                (Integer) p.get("stock"),
                (String) p.get("category"),
                (String) p.get("status")
            ))
            .toList();

        return ProductListResponse.builder()
            .products(productSummaries)
            .page(page)
            .size(size)
            .total(list.size())
            .build();
    }

    @Operation(summary = "상품 상세 조회", description = "상품의 상세 정보와 옵션을 조회합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ProductDetailResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "상품을 찾을 수 없음")
    })
    @GetMapping("/{productId}")
    public ProductDetailResponse getProductDetail(
        @Parameter(description = "상품 ID", example = "1")
        @PathVariable Long productId
    ) {
        Map<String, Object> product = PRODUCTS.get(productId);
        if (product == null) {
            throw new RuntimeException("Product not found: " + productId);
        }

        List<Map<String, Object>> options = PRODUCT_OPTIONS.getOrDefault(productId, Collections.emptyList());
        List<ProductDetailResponse.ProductOption> productOptions = options.stream()
            .map(opt -> new ProductDetailResponse.ProductOption(
                (Long) opt.get("id"),
                (String) opt.get("name"),
                (Integer) opt.get("additionalPrice"),
                (Integer) opt.get("stock")
            ))
            .toList();

        return ProductDetailResponse.builder()
            .id((Long) product.get("id"))
            .name((String) product.get("name"))
            .price((Integer) product.get("price"))
            .stock((Integer) product.get("stock"))
            .category((String) product.get("category"))
            .status((String) product.get("status"))
            .options(productOptions)
            .build();
    }

    @Operation(summary = "인기 상품 조회", description = "최근 3일간 판매량 기준 상위 5개 상품을 조회합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = PopularProductsResponse.class)
            )
        )
    })
    @GetMapping("/popular")
    public PopularProductsResponse getPopularProducts() {
        List<PopularProductsResponse.PopularProduct> popularProducts = new ArrayList<>();

        for (Map<String, Object> popular : POPULAR_PRODUCTS) {
            Long productId = (Long) popular.get("productId");
            Map<String, Object> product = PRODUCTS.get(productId);

            if (product != null) {
                popularProducts.add(PopularProductsResponse.PopularProduct.builder()
                    .productId(productId)
                    .name((String) product.get("name"))
                    .price((Integer) product.get("price"))
                    .totalSales((Integer) popular.get("totalSales"))
                    .category((String) product.get("category"))
                    .status((String) product.get("status"))
                    .build()
                );
            }
        }

        return PopularProductsResponse.builder()
            .products(popularProducts)
            .totalCount(popularProducts.size())
            .build();
    }
}
