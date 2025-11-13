package org.hhplus.hhecommerce.application.cart;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.cart.DeleteCartResponse;
import org.hhplus.hhecommerce.domain.cart.Cart;
import org.hhplus.hhecommerce.domain.cart.exception.CartErrorCode;
import org.hhplus.hhecommerce.infrastructure.repository.cart.CartRepository;
import org.hhplus.hhecommerce.domain.cart.exception.CartException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeleteCartUseCase {

    private final CartRepository cartRepository;

    public DeleteCartResponse execute(Long cartId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new CartException(CartErrorCode.CART_ITEM_NOT_FOUND));

        cartRepository.delete(cart);

        return new DeleteCartResponse(cartId, "장바구니에서 삭제되었습니다");
    }
}
