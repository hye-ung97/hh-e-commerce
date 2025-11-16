package org.hhplus.hhecommerce.application.cart;

import org.hhplus.hhecommerce.api.dto.cart.CartItemResponse;
import org.hhplus.hhecommerce.api.dto.cart.UpdateCartRequest;
import org.hhplus.hhecommerce.domain.cart.Cart;
import org.hhplus.hhecommerce.domain.cart.exception.CartException;
import org.hhplus.hhecommerce.domain.cart.CartRepository;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.hhplus.hhecommerce.domain.product.ProductStatus;
import org.hhplus.hhecommerce.domain.product.exception.ProductException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateCartUseCaseTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductOptionRepository productOptionRepository;

    @InjectMocks
    private UpdateCartUseCase updateCartUseCase;

    @Test
    @DisplayName("정상적으로 장바구니 수량을 변경할 수 있다")
    void 정상적으로_장바구니_수량을_변경할_수_있다() {
        // Given
        Long userId = 1L;
        Long cartId = 1L;
        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 1500000, 10);

        Cart cart = new Cart(userId, option.getId(), 2);
        cart.setId(cartId);

        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart));
        when(productOptionRepository.findById(option.getId())).thenReturn(Optional.of(option));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateCartRequest request = new UpdateCartRequest(5);

        // When
        CartItemResponse response = updateCartUseCase.execute(cartId, request);

        // Then
        assertThat(response.quantity()).isEqualTo(5);
        assertThat(response.totalPrice()).isEqualTo(7500000); // 1500000 * 5
    }

    @Test
    @DisplayName("재고가 부족하면 수량을 변경할 수 없다")
    void 재고가_부족하면_수량을_변경할_수_없다() {
        // Given
        Long userId = 1L;
        Long cartId = 1L;
        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 1500000, 5);

        Cart cart = new Cart(userId, option.getId(), 2);
        cart.setId(cartId);

        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart));
        when(productOptionRepository.findById(option.getId())).thenReturn(Optional.of(option));

        UpdateCartRequest request = new UpdateCartRequest(10);

        // When & Then
        assertThatThrownBy(() -> updateCartUseCase.execute(cartId, request))
            .isInstanceOf(ProductException.class);
    }

    @Test
    @DisplayName("존재하지 않는 장바구니 항목은 수정할 수 없다")
    void 존재하지_않는_장바구니_항목은_수정할_수_없다() {
        // Given
        when(cartRepository.findById(999L)).thenReturn(Optional.empty());

        UpdateCartRequest request = new UpdateCartRequest(5);

        // When & Then
        assertThatThrownBy(() -> updateCartUseCase.execute(999L, request))
            .isInstanceOf(CartException.class);
    }
}
