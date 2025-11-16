package org.hhplus.hhecommerce.domain.order;

import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductStatus;
import org.hhplus.hhecommerce.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class OrderTest {

    private User user;
    private Product product;
    private ProductOption option;

    @BeforeEach
    void setUp() {
        user = new User(1L, "테스트유저", "test@test.com");
        product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        option = new ProductOption(1L, product.getId(), "RAM", "16GB", 50000, 10);
    }

    @Test
    @DisplayName("주문을 생성할 수 있다")
    void 주문을_생성할_수_있다() {
        // Given
        OrderItem item = new OrderItem(option.getId(), 2, 50000);

        // When
        Order order = Order.create(user.getId(), List.of(item), 0);

        // Then
        assertThat(order).isNotNull();
        assertThat(order.getUserId()).isEqualTo(user.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualTo(100000); // 50000 * 2
        assertThat(order.getDiscountAmount()).isEqualTo(0);
        assertThat(order.getFinalAmount()).isEqualTo(100000);
        assertThat(order.getOrderItems()).hasSize(1);
        assertThat(order.getOrderedAt()).isNotNull();
    }

    @Test
    @DisplayName("할인 금액을 적용하여 주문을 생성할 수 있다")
    void 할인_금액을_적용하여_주문을_생성할_수_있다() {
        // Given
        OrderItem item = new OrderItem(option.getId(), 2, 50000);

        // When
        Order order = Order.create(user.getId(), List.of(item), 10000);

        // Then
        assertThat(order.getTotalAmount()).isEqualTo(100000); // 50000 * 2
        assertThat(order.getDiscountAmount()).isEqualTo(10000);
        assertThat(order.getFinalAmount()).isEqualTo(90000); // 100000 - 10000
    }

    @Test
    @DisplayName("여러 상품으로 주문을 생성할 수 있다")
    void 여러_상품으로_주문을_생성할_수_있다() {
        // Given
        Product product2 = new Product(2L, "키보드", "기계식 키보드", "전자제품", ProductStatus.ACTIVE);
        ProductOption option2 = new ProductOption(2L, product2.getId(), "스위치", "청축", 100000, 20);

        OrderItem item1 = new OrderItem(option.getId(), 2, 50000);
        OrderItem item2 = new OrderItem(option2.getId(), 1, 100000);

        // When
        Order order = Order.create(user.getId(), List.of(item1, item2), 0);

        // Then
        assertThat(order.getTotalAmount()).isEqualTo(200000); // (50000*2) + (100000*1)
        assertThat(order.getOrderItems()).hasSize(2);
    }

    @Test
    @DisplayName("주문을 확정할 수 있다")
    void 주문을_확정할_수_있다() {
        // Given
        OrderItem item = new OrderItem(option.getId(), 2, 50000);
        Order order = Order.create(user.getId(), List.of(item), 0);

        // When
        order.confirm();

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("대기 상태가 아니면 확정할 수 없다")
    void 대기_상태가_아니면_확정할_수_없다() {
        // Given
        OrderItem item = new OrderItem(option.getId(), 2, 50000);
        Order order = Order.create(user.getId(), List.of(item), 0);
        order.confirm();

        // When & Then
        assertThatThrownBy(() -> order.confirm())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("주문을 취소할 수 있다")
    void 주문을_취소할_수_있다() {
        // Given
        OrderItem item = new OrderItem(option.getId(), 2, 50000);
        Order order = Order.create(user.getId(), List.of(item), 0);

        // When
        order.cancel();

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("확정된 주문도 취소할 수 있다")
    void 확정된_주문도_취소할_수_있다() {
        // Given
        OrderItem item = new OrderItem(option.getId(), 2, 50000);
        Order order = Order.create(user.getId(), List.of(item), 0);
        order.confirm();

        // When
        order.cancel();

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("완료된 주문은 취소할 수 없다")
    void 완료된_주문은_취소할_수_없다() {
        // Given
        OrderItem item = new OrderItem(option.getId(), 2, 50000);
        Order order = Order.create(user.getId(), List.of(item), 0);
        order.confirm();
        order.complete();

        // When & Then
        assertThatThrownBy(() -> order.cancel())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("이미 취소된 주문은 다시 취소할 수 없다")
    void 이미_취소된_주문은_다시_취소할_수_없다() {
        // Given
        OrderItem item = new OrderItem(option.getId(), 2, 50000);
        Order order = Order.create(user.getId(), List.of(item), 0);
        order.cancel();

        // When & Then
        assertThatThrownBy(() -> order.cancel())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("주문을 완료할 수 있다")
    void 주문을_완료할_수_있다() {
        // Given
        OrderItem item = new OrderItem(option.getId(), 2, 50000);
        Order order = Order.create(user.getId(), List.of(item), 0);
        order.confirm();

        // When
        order.complete();

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("확정되지 않은 주문은 완료할 수 없다")
    void 확정되지_않은_주문은_완료할_수_없다() {
        // Given
        OrderItem item = new OrderItem(option.getId(), 2, 50000);
        Order order = Order.create(user.getId(), List.of(item), 0);

        // When & Then
        assertThatThrownBy(() -> order.complete())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("주문 생성 시 초기 상태는 PENDING이다")
    void 주문_생성_시_초기_상태는_PENDING이다() {
        // Given
        OrderItem item = new OrderItem(option.getId(), 2, 50000);

        // When
        Order order = Order.create(user.getId(), List.of(item), 0);

        // Then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }
}
