package org.hhplus.hhecommerce.application.cart;

import org.hhplus.hhecommerce.api.dto.cart.CartListResponse;
import org.hhplus.hhecommerce.domain.cart.Cart;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.cart.CartRepository;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.hhplus.hhecommerce.domain.product.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetCartListUseCaseTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductOptionRepository productOptionRepository;

    @InjectMocks
    private GetCartListUseCase getCartListUseCase;

    @Test
    @DisplayName("정상적으로 장바구니 목록을 조회할 수 있다")
    void 정상적으로_장바구니_목록을_조회할_수_있다() {
        // Given
        Long userId = 1L;
        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 1500000, 10);

        Cart cart = new Cart(userId, option.getId(), 2);
        cart.setId(1L);

        when(cartRepository.findByUserId(eq(userId), any(Pageable.class))).thenReturn(Collections.singletonList(cart));
        when(cartRepository.countByUserId(userId)).thenReturn(1);
        when(productOptionRepository.findById(option.getId())).thenReturn(Optional.of(option));

        // When
        CartListResponse response = getCartListUseCase.execute(userId, 0, 10);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(1);
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.totalAmount()).isEqualTo(3000000); // 1500000 * 2
    }

    @Test
    @DisplayName("빈 장바구니를 조회할 수 있다")
    void 빈_장바구니를_조회할_수_있다() {
        // Given
        Long userId = 1L;

        when(cartRepository.findByUserId(eq(userId), any(Pageable.class))).thenReturn(Collections.emptyList());
        when(cartRepository.countByUserId(userId)).thenReturn(0);

        // When
        CartListResponse response = getCartListUseCase.execute(userId, 0, 10);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.items()).isEmpty();
        assertThat(response.totalCount()).isEqualTo(0);
        assertThat(response.totalAmount()).isEqualTo(0);
    }

    @Test
    @DisplayName("여러 상품을 장바구니에 담을 수 있다")
    void 여러_상품을_장바구니에_담을_수_있다() {
        // Given
        Long userId = 1L;
        Product product1 = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        Product product2 = new Product(2L, "키보드", "기계식 키보드", "전자제품", ProductStatus.ACTIVE);

        ProductOption option1 = new ProductOption(1L, product1, "RAM", "16GB", 1500000, 10);
        ProductOption option2 = new ProductOption(2L, product2, "스위치", "청축", 100000, 20);

        Cart cart1 = new Cart(userId, option1.getId(), 2);
        cart1.setId(1L);
        Cart cart2 = new Cart(userId, option2.getId(), 3);
        cart2.setId(2L);

        when(cartRepository.findByUserId(eq(userId), any(Pageable.class))).thenReturn(Arrays.asList(cart1, cart2));
        when(cartRepository.countByUserId(userId)).thenReturn(2);
        when(productOptionRepository.findById(option1.getId())).thenReturn(Optional.of(option1));
        when(productOptionRepository.findById(option2.getId())).thenReturn(Optional.of(option2));

        // When
        CartListResponse response = getCartListUseCase.execute(userId, 0, 10);

        // Then
        assertThat(response.items()).hasSize(2);
        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.totalAmount()).isEqualTo(3300000); // (1500000*2) + (100000*3)
    }
}
