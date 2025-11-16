package org.hhplus.hhecommerce.application.cart;

import org.hhplus.hhecommerce.api.dto.cart.AddCartRequest;
import org.hhplus.hhecommerce.api.dto.cart.CartItemResponse;
import org.hhplus.hhecommerce.domain.cart.Cart;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.cart.CartRepository;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
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
class AddToCartUseCaseTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductOptionRepository productOptionRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private AddToCartUseCase addToCartUseCase;

    @Test
    @DisplayName("정상적으로 장바구니에 상품을 추가할 수 있다")
    void 정상적으로_장바구니에_상품을_추가할_수_있다() {
        // Given
        Long userId = 1L;
        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product.getId(), "RAM", "16GB", 1500000, 10);

        when(productOptionRepository.findById(1L)).thenReturn(Optional.of(option));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(cartRepository.findByUserIdAndProductOptionId(userId, 1L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> {
            Cart cart = invocation.getArgument(0);
            cart.setId(1L);
            return cart;
        });

        AddCartRequest request = new AddCartRequest(option.getId(), 2);

        // When
        CartItemResponse response = addToCartUseCase.execute(userId, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.productOptionId()).isEqualTo(option.getId());
        assertThat(response.quantity()).isEqualTo(2);
        assertThat(response.totalPrice()).isEqualTo(3000000);
    }

    @Test
    @DisplayName("이미 장바구니에 있는 상품을 추가하면 수량이 증가한다")
    void 이미_장바구니에_있는_상품을_추가하면_수량이_증가한다() {
        // Given
        Long userId = 1L;
        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product.getId(), "RAM", "16GB", 1500000, 10);

        Cart existingCart = new Cart(userId, option.getId(), 2);
        existingCart.setId(1L);

        when(productOptionRepository.findById(1L)).thenReturn(Optional.of(option));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));
        when(cartRepository.findByUserIdAndProductOptionId(userId, 1L)).thenReturn(Optional.of(existingCart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AddCartRequest request = new AddCartRequest(option.getId(), 3);

        // When
        CartItemResponse response = addToCartUseCase.execute(userId, request);

        // Then
        assertThat(response.quantity()).isEqualTo(5); // 2 + 3
        assertThat(response.totalPrice()).isEqualTo(7500000); // 1500000 * 5
    }

    @Test
    @DisplayName("재고가 부족한 상품은 장바구니에 추가할 수 없다")
    void 재고가_부족한_상품은_장바구니에_추가할_수_없다() {
        // Given
        Long userId = 1L;
        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product.getId(), "RAM", "16GB", 1500000, 5);

        when(productOptionRepository.findById(1L)).thenReturn(Optional.of(option));

        AddCartRequest request = new AddCartRequest(option.getId(), 10);

        // When & Then
        assertThatThrownBy(() -> addToCartUseCase.execute(userId, request))
            .isInstanceOf(ProductException.class);
    }

    @Test
    @DisplayName("존재하지 않는 상품은 장바구니에 추가할 수 없다")
    void 존재하지_않는_상품은_장바구니에_추가할_수_없다() {
        // Given
        Long userId = 1L;

        when(productOptionRepository.findById(999L)).thenReturn(Optional.empty());

        AddCartRequest request = new AddCartRequest(999L, 2);

        // When & Then
        assertThatThrownBy(() -> addToCartUseCase.execute(userId, request))
            .isInstanceOf(ProductException.class);
    }
}
