package org.hhplus.hhecommerce.application.cart;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.cart.CartListResponse;
import org.hhplus.hhecommerce.domain.cart.Cart;
import org.hhplus.hhecommerce.domain.cart.CartRepository;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.hhplus.hhecommerce.domain.product.exception.ProductErrorCode;
import org.hhplus.hhecommerce.domain.product.exception.ProductException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetCartListUseCase {

    private final CartRepository cartRepository;
    private final ProductOptionRepository productOptionRepository;

    public CartListResponse execute(Long userId, int page, int size) {
        List<Cart> carts = cartRepository.findByUserId(userId, PageRequest.of(page, size));
        int totalCount = cartRepository.countByUserId(userId);

        List<CartListResponse.CartItem> items = carts.stream()
                .map(cart -> {
                    ProductOption option = productOptionRepository.findById(cart.getProductOptionId())
                            .orElseThrow(()-> new ProductException(ProductErrorCode.PRODUCT_OPTION_NOT_FOUND));

                    return new CartListResponse.CartItem(
                            cart.getId(),
                            cart.getUserId(),
                            cart.getProductOptionId(),
                            option.getProduct().getName(),
                            option.getOptionName() + ": " + option.getOptionValue(),
                            option.getPrice(),
                            cart.getQuantity(),
                            option.calculateTotalPrice(cart.getQuantity())
                    );
                })
                .collect(Collectors.toList());

        int totalAmount = items.stream().mapToInt(CartListResponse.CartItem::totalPrice).sum();

        return new CartListResponse(items, totalCount, totalAmount);
    }
}
