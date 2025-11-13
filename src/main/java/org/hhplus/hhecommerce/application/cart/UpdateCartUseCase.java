package org.hhplus.hhecommerce.application.cart;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.cart.CartItemResponse;
import org.hhplus.hhecommerce.api.dto.cart.UpdateCartRequest;
import org.hhplus.hhecommerce.domain.cart.Cart;
import org.hhplus.hhecommerce.domain.cart.exception.CartErrorCode;
import org.hhplus.hhecommerce.infrastructure.repository.cart.CartRepository;
import org.hhplus.hhecommerce.domain.cart.exception.CartException;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.exception.ProductErrorCode;
import org.hhplus.hhecommerce.domain.product.exception.ProductException;
import org.hhplus.hhecommerce.infrastructure.repository.product.ProductOptionRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UpdateCartUseCase {

    private final CartRepository cartRepository;
    private final ProductOptionRepository productOptionRepository;

    public CartItemResponse execute(Long cartId, UpdateCartRequest request) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new CartException(CartErrorCode.CART_ITEM_NOT_FOUND));

        ProductOption option = productOptionRepository.findById(cart.getProductOptionId())
                .orElseThrow(()-> new ProductException(ProductErrorCode.PRODUCT_OPTION_NOT_FOUND));

        if (!option.hasStock(request.getQuantity())) {
            throw new ProductException(ProductErrorCode.INSUFFICIENT_STOCK);
        }

        cart.updateQuantity(request.getQuantity());
        cartRepository.save(cart);

        return new CartItemResponse(
                cart.getId(),
                cart.getUserId(),
                cart.getProductOptionId(),
                option.getProduct().getName(),
                option.getOptionName() + ": " + option.getOptionValue(),
                option.getPrice(),
                cart.getQuantity(),
                option.calculateTotalPrice(cart.getQuantity()),
                "장바구니가 수정되었습니다"
        );
    }
}
