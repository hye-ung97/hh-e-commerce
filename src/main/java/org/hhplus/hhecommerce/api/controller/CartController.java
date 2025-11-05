package org.hhplus.hhecommerce.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.cart.*;
import org.hhplus.hhecommerce.application.cart.AddToCartUseCase;
import org.hhplus.hhecommerce.application.cart.DeleteCartUseCase;
import org.hhplus.hhecommerce.application.cart.GetCartListUseCase;
import org.hhplus.hhecommerce.application.cart.UpdateCartUseCase;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Cart", description = "장바구니 관리 API")
@RestController
@RequestMapping("/api/carts")
@RequiredArgsConstructor
public class CartController {

    private final GetCartListUseCase getCartListUseCase;
    private final AddToCartUseCase addToCartUseCase;
    private final UpdateCartUseCase updateCartUseCase;
    private final DeleteCartUseCase deleteCartUseCase;

    @Operation(summary = "장바구니 조회")
    @GetMapping
    public CartListResponse getCart(
        @Parameter(description = "사용자 ID") @RequestParam(defaultValue = "1") Long userId,
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "페이지 크기", example = "20") @RequestParam(defaultValue = "20") int size
    ) {
        return getCartListUseCase.execute(userId, page, size);
    }

    @Operation(summary = "장바구니 상품 추가")
    @PostMapping
    public CartItemResponse addToCart(
        @Parameter(description = "사용자 ID") @RequestParam(defaultValue = "1") Long userId,
        @RequestBody AddCartRequest request
    ) {
        return addToCartUseCase.execute(userId, request);
    }

    @Operation(summary = "장바구니 상품 수량 변경")
    @PatchMapping("/{cartId}")
    public CartItemResponse updateCart(
        @Parameter(description = "장바구니 ID") @PathVariable Long cartId,
        @RequestBody UpdateCartRequest request
    ) {
        return updateCartUseCase.execute(cartId, request);
    }

    @Operation(summary = "장바구니 상품 삭제")
    @DeleteMapping("/{cartId}")
    public DeleteCartResponse deleteCart(
        @Parameter(description = "장바구니 ID") @PathVariable Long cartId
    ) {
        return deleteCartUseCase.execute(cartId);
    }
}
