package org.hhplus.hhecommerce.application.cart;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.cart.AddCartRequest;
import org.hhplus.hhecommerce.api.dto.cart.CartItemResponse;
import org.hhplus.hhecommerce.domain.cart.Cart;
import org.hhplus.hhecommerce.domain.cart.CartRepository;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.hhplus.hhecommerce.domain.product.exception.ProductErrorCode;
import org.hhplus.hhecommerce.domain.product.exception.ProductException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AddToCartUseCase {

    private final CartRepository cartRepository;
    private final ProductOptionRepository productOptionRepository;

    public CartItemResponse execute(Long userId, AddCartRequest request) {
        ProductOption option = productOptionRepository.findById(request.getProductOptionId())
                .orElseThrow(()-> new ProductException(ProductErrorCode.PRODUCT_OPTION_NOT_FOUND));

        if (!option.hasStock(request.getQuantity())) {
            throw new ProductException(ProductErrorCode.INSUFFICIENT_STOCK);
        }

        Cart cart = cartRepository.findByUserIdAndProductOptionId(userId, request.getProductOptionId())
                .orElse(null);

        if (cart != null) {
            cart.addQuantity(request.getQuantity());
        } else {
            cart = new Cart(userId, request.getProductOptionId(), request.getQuantity());
        }

        cartRepository.save(cart);

        return CartItemResponse.builder()
                .id(cart.getId())
                .userId(cart.getUserId())
                .productOptionId(cart.getProductOptionId())
                .productName(option.getProduct().getName())
                .optionName(option.getOptionName() + ": " + option.getOptionValue())
                .price(option.getPrice())
                .quantity(cart.getQuantity())
                .totalPrice(option.calculateTotalPrice(cart.getQuantity()))
                .build();
    }
}
