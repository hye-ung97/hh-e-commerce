package org.hhplus.hhecommerce.domain.order;

import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OrderItemTest {

    private Product product;
    private ProductOption option;

    @BeforeEach
    void setUp() {
        product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        option = new ProductOption(1L, product.getId(), "RAM", "16GB", 50000, 10);
    }

    @Test
    @DisplayName("주문 항목을 생성할 수 있다")
    void 주문_항목을_생성할_수_있다() {
        // When
        OrderItem orderItem = new OrderItem(option.getId(), 2, 50000);

        // Then
        assertThat(orderItem.getProductOptionId()).isEqualTo(option.getId());
        assertThat(orderItem.getQuantity()).isEqualTo(2);
        assertThat(orderItem.getUnitPrice()).isEqualTo(50000);
        assertThat(orderItem.getSubTotal()).isEqualTo(100000); // 50000 * 2
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.ORDERED);
    }

    @Test
    @DisplayName("주문 항목의 총 가격을 계산할 수 있다")
    void 주문_항목의_총_가격을_계산할_수_있다() {
        // Given
        OrderItem orderItem = new OrderItem(option.getId(), 3, 50000);

        // When
        int totalPrice = orderItem.getTotalPrice();

        // Then
        assertThat(totalPrice).isEqualTo(150000); // 50000 * 3
        assertThat(totalPrice).isEqualTo(orderItem.getSubTotal());
    }

    @Test
    @DisplayName("주문 항목을 취소할 수 있다")
    void 주문_항목을_취소할_수_있다() {
        // Given
        OrderItem orderItem = new OrderItem(option.getId(), 2, 50000);

        // When
        orderItem.cancel();

        // Then
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.CANCELLED);
    }

    @Test
    @DisplayName("이미 취소된 주문 항목은 다시 취소할 수 없다")
    void 이미_취소된_주문_항목은_다시_취소할_수_없다() {
        // Given
        OrderItem orderItem = new OrderItem(option.getId(), 2, 50000);
        orderItem.cancel();

        // When & Then
        assertThatThrownBy(() -> orderItem.cancel())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("주문 항목 생성 시 초기 상태는 ORDERED이다")
    void 주문_항목_생성_시_초기_상태는_ORDERED이다() {
        // When
        OrderItem orderItem = new OrderItem(option.getId(), 2, 50000);

        // Then
        assertThat(orderItem.getStatus()).isEqualTo(OrderItemStatus.ORDERED);
    }

    @Test
    @DisplayName("수량이 1인 주문 항목도 생성할 수 있다")
    void 수량이_1인_주문_항목도_생성할_수_있다() {
        // When
        OrderItem orderItem = new OrderItem(option.getId(), 1, 50000);

        // Then
        assertThat(orderItem.getQuantity()).isEqualTo(1);
        assertThat(orderItem.getSubTotal()).isEqualTo(50000);
    }

    @Test
    @DisplayName("대량 수량의 주문 항목도 생성할 수 있다")
    void 대량_수량의_주문_항목도_생성할_수_있다() {
        // When
        OrderItem orderItem = new OrderItem(option.getId(), 100, 50000);

        // Then
        assertThat(orderItem.getQuantity()).isEqualTo(100);
        assertThat(orderItem.getSubTotal()).isEqualTo(5000000); // 50000 * 100
    }
}
