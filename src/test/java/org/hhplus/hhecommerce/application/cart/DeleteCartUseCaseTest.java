package org.hhplus.hhecommerce.application.cart;

import org.hhplus.hhecommerce.api.dto.cart.DeleteCartResponse;
import org.hhplus.hhecommerce.domain.cart.Cart;
import org.hhplus.hhecommerce.domain.cart.exception.CartException;
import org.hhplus.hhecommerce.domain.cart.CartRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteCartUseCaseTest {

    @Mock
    private CartRepository cartRepository;

    @InjectMocks
    private DeleteCartUseCase deleteCartUseCase;

    @Test
    @DisplayName("정상적으로 장바구니에서 상품을 삭제할 수 있다")
    void 정상적으로_장바구니에서_상품을_삭제할_수_있다() {
        // Given
        Long cartId = 1L;
        Cart cart = new Cart(1L, 1L, 2);
        cart.setId(cartId);

        when(cartRepository.findById(cartId)).thenReturn(Optional.of(cart));
        doNothing().when(cartRepository).delete(cart);

        // When
        DeleteCartResponse response = deleteCartUseCase.execute(cartId);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(cartId);
        assertThat(response.message()).contains("삭제");

        verify(cartRepository, times(1)).findById(cartId);
        verify(cartRepository, times(1)).delete(cart);
    }

    @Test
    @DisplayName("존재하지 않는 장바구니 항목은 삭제할 수 없다")
    void 존재하지_않는_장바구니_항목은_삭제할_수_없다() {
        // Given
        when(cartRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> deleteCartUseCase.execute(999L))
            .isInstanceOf(CartException.class);
    }
}
