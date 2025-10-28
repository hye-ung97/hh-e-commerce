package org.hhplus.hhecommerce.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.hhplus.hhecommerce.dto.cart.*;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Tag(name = "Cart", description = "장바구니 관리 API")
@RestController
@RequestMapping("/api/carts")
public class CartController {

    private static final AtomicLong CART_ID_GENERATOR = new AtomicLong(1);
    private static final Map<Long, Map<String, Object>> CARTS = new ConcurrentHashMap<>();

    private static final Map<Long, Map<String, Object>> PRODUCT_OPTIONS = Map.of(
        1L, Map.of("id", 1L, "productId", 1L, "productName", "노트북", "optionName", "색상: 실버, 용량: 256GB", "price", 1500000, "stock", 5),
        2L, Map.of("id", 2L, "productId", 1L, "productName", "노트북", "optionName", "색상: 실버, 용량: 512GB", "price", 1700000, "stock", 5),
        3L, Map.of("id", 3L, "productId", 2L, "productName", "키보드", "optionName", "색상: 블랙", "price", 120000, "stock", 25),
        4L, Map.of("id", 4L, "productId", 2L, "productName", "키보드", "optionName", "색상: 화이트", "price", 120000, "stock", 25),
        5L, Map.of("id", 5L, "productId", 3L, "productName", "마우스", "optionName", "색상: 블랙", "price", 50000, "stock", 50)
    );

    @Operation(summary = "장바구니 조회", description = "사용자의 장바구니 목록을 조회합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "조회 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CartListResponse.class)
            )
        )
    })
    @GetMapping
    public CartListResponse getCarts(
        @Parameter(description = "사용자 ID", example = "1")
        @RequestParam(defaultValue = "1") Long userId
    ) {
        List<CartListResponse.CartItem> userCarts = CARTS.values().stream()
            .filter(cart -> userId.equals(cart.get("userId")))
            .map(cart -> {
                Long productOptionId = (Long) cart.get("productOptionId");
                Map<String, Object> option = PRODUCT_OPTIONS.get(productOptionId);

                CartListResponse.CartItem.CartItemBuilder builder = CartListResponse.CartItem.builder()
                    .id((Long) cart.get("id"))
                    .userId((Long) cart.get("userId"))
                    .productOptionId(productOptionId)
                    .quantity((Integer) cart.get("quantity"))
                    .createdAt((LocalDateTime) cart.get("createdAt"))
                    .updatedAt((LocalDateTime) cart.get("updatedAt"));

                if (option != null) {
                    int quantity = (Integer) cart.get("quantity");
                    int price = (Integer) option.get("price");
                    builder.productName((String) option.get("productName"))
                        .optionName((String) option.get("optionName"))
                        .price(price)
                        .totalPrice(price * quantity);
                }

                return builder.build();
            })
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .toList();

        int totalAmount = userCarts.stream()
            .mapToInt(cart -> cart.getTotalPrice() != null ? cart.getTotalPrice() : 0)
            .sum();

        return CartListResponse.builder()
            .items(userCarts)
            .totalAmount(totalAmount)
            .totalCount(userCarts.size())
            .build();
    }

    @Operation(summary = "장바구니 상품 추가", description = "장바구니에 상품을 추가합니다. 이미 있는 상품은 수량이 증가합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "추가 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CartItemResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "재고 부족 또는 잘못된 요청")
    })
    @PostMapping
    public CartItemResponse addCartItem(
        @Parameter(description = "사용자 ID", example = "1")
        @RequestParam(defaultValue = "1") Long userId,
        @RequestBody Map<String, Object> request
    ) {
        Long productOptionId = ((Number) request.get("productOptionId")).longValue();
        int quantity = ((Number) request.get("quantity")).intValue();

        if (quantity <= 0) {
            throw new RuntimeException("Quantity must be greater than 0");
        }

        Map<String, Object> option = PRODUCT_OPTIONS.get(productOptionId);
        if (option == null) {
            throw new RuntimeException("Product option not found: " + productOptionId);
        }

        int stock = (Integer) option.get("stock");
        if (stock < quantity) {
            throw new RuntimeException("Insufficient stock. Available: " + stock);
        }

        Optional<Map<String, Object>> existingCart = CARTS.values().stream()
            .filter(cart -> userId.equals(cart.get("userId")) &&
                productOptionId.equals(cart.get("productOptionId")))
            .findFirst();

        Map<String, Object> cart;
        if (existingCart.isPresent()) {
            cart = existingCart.get();
            int currentQuantity = (Integer) cart.get("quantity");
            int newQuantity = currentQuantity + quantity;

            if (stock < newQuantity) {
                throw new RuntimeException("Insufficient stock. Available: " + stock);
            }

            cart.put("quantity", newQuantity);
            cart.put("updatedAt", LocalDateTime.now());
        } else {
            Long cartId = CART_ID_GENERATOR.getAndIncrement();
            cart = new HashMap<>();
            cart.put("id", cartId);
            cart.put("userId", userId);
            cart.put("productOptionId", productOptionId);
            cart.put("quantity", quantity);
            cart.put("createdAt", LocalDateTime.now());
            cart.put("updatedAt", LocalDateTime.now());
            CARTS.put(cartId, cart);
        }

        int finalQuantity = (Integer) cart.get("quantity");
        int price = (Integer) option.get("price");

        return CartItemResponse.builder()
            .id((Long) cart.get("id"))
            .userId(userId)
            .productOptionId(productOptionId)
            .productName((String) option.get("productName"))
            .optionName((String) option.get("optionName"))
            .price(price)
            .quantity(finalQuantity)
            .totalPrice(price * finalQuantity)
            .createdAt((LocalDateTime) cart.get("createdAt"))
            .updatedAt((LocalDateTime) cart.get("updatedAt"))
            .message("Cart item added successfully")
            .build();
    }

    @Operation(summary = "장바구니 상품 수량 변경", description = "장바구니 상품의 수량을 변경합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "변경 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CartItemResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "재고 부족 또는 장바구니를 찾을 수 없음")
    })
    @PatchMapping("/{cartId}")
    public CartItemResponse updateCartItem(
        @Parameter(description = "장바구니 항목 ID", example = "1")
        @PathVariable Long cartId,
        @Parameter(description = "사용자 ID", example = "1")
        @RequestParam(defaultValue = "1") Long userId,
        @RequestBody Map<String, Object> request
    ) {
        int quantity = ((Number) request.get("quantity")).intValue();

        if (quantity <= 0) {
            throw new RuntimeException("Quantity must be greater than 0");
        }

        Map<String, Object> cart = CARTS.get(cartId);
        if (cart == null || !userId.equals(cart.get("userId"))) {
            throw new RuntimeException("Cart not found: " + cartId);
        }

        Long productOptionId = (Long) cart.get("productOptionId");
        Map<String, Object> option = PRODUCT_OPTIONS.get(productOptionId);
        if (option == null) {
            throw new RuntimeException("Product option not found: " + productOptionId);
        }

        int stock = (Integer) option.get("stock");
        if (stock < quantity) {
            throw new RuntimeException("Insufficient stock. Available: " + stock);
        }

        cart.put("quantity", quantity);
        cart.put("updatedAt", LocalDateTime.now());

        int price = (Integer) option.get("price");

        return CartItemResponse.builder()
            .id(cartId)
            .userId(userId)
            .productOptionId(productOptionId)
            .productName((String) option.get("productName"))
            .optionName((String) option.get("optionName"))
            .price(price)
            .quantity(quantity)
            .totalPrice(price * quantity)
            .createdAt((LocalDateTime) cart.get("createdAt"))
            .updatedAt((LocalDateTime) cart.get("updatedAt"))
            .message("Cart item updated successfully")
            .build();
    }

    @Operation(summary = "장바구니 상품 삭제", description = "장바구니에서 상품을 삭제합니다.")
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "삭제 성공",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = DeleteCartResponse.class)
            )
        ),
        @ApiResponse(responseCode = "400", description = "장바구니를 찾을 수 없음")
    })
    @DeleteMapping("/{cartId}")
    public DeleteCartResponse deleteCartItem(
        @Parameter(description = "장바구니 항목 ID", example = "1")
        @PathVariable Long cartId,
        @Parameter(description = "사용자 ID", example = "1")
        @RequestParam(defaultValue = "1") Long userId
    ) {
        Map<String, Object> cart = CARTS.get(cartId);
        if (cart == null || !userId.equals(cart.get("userId"))) {
            throw new RuntimeException("Cart not found: " + cartId);
        }

        CARTS.remove(cartId);

        return DeleteCartResponse.builder()
            .id(cartId)
            .message("Cart item deleted successfully")
            .build();
    }
}
