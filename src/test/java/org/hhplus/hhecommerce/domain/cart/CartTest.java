package org.hhplus.hhecommerce.domain.cart;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CartTest {

    private Cart cart;

    @BeforeEach
    void setUp() {
        cart = new Cart(1L, 100L, 2);
    }

    @Test
    @DisplayName("장바구니 아이템을 생성할 수 있다")
    void 장바구니_아이템을_생성할_수_있다() {
        // When
        Cart newCart = new Cart(1L, 100L, 3);

        // Then
        assertEquals(1L, newCart.getUserId());
        assertEquals(100L, newCart.getProductOptionId());
        assertEquals(3, newCart.getQuantity());
    }

    @Test
    @DisplayName("정상적으로 수량을 변경할 수 있다")
    void 정상적으로_수량을_변경할_수_있다() {
        // When
        cart.updateQuantity(5);

        // Then
        assertEquals(5, cart.getQuantity());
    }

    @Test
    @DisplayName("수량을 0 이하로 변경할 수 없다")
    void 수량을_0_이하로_변경할_수_없다() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            cart.updateQuantity(0);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            cart.updateQuantity(-1);
        });

        assertEquals(2, cart.getQuantity()); // 수량은 변하지 않음
    }

    @Test
    @DisplayName("정상적으로 수량을 추가할 수 있다")
    void 정상적으로_수량을_추가할_수_있다() {
        // When
        cart.addQuantity(3);

        // Then
        assertEquals(5, cart.getQuantity()); // 2 + 3
    }

    @Test
    @DisplayName("수량을 여러 번 추가할 수 있다")
    void 수량을_여러_번_추가할_수_있다() {
        // When
        cart.addQuantity(1);
        cart.addQuantity(2);
        cart.addQuantity(3);

        // Then
        assertEquals(8, cart.getQuantity()); // 2 + 1 + 2 + 3
    }

    @Test
    @DisplayName("수량 추가 후 변경을 할 수 있다")
    void 수량_추가_후_변경을_할_수_있다() {
        // When
        cart.addQuantity(3); // 2 + 3 = 5
        cart.updateQuantity(10); // 10으로 변경

        // Then
        assertEquals(10, cart.getQuantity());
    }
}
