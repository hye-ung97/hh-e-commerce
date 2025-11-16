package org.hhplus.hhecommerce.application.order;

import org.hhplus.hhecommerce.api.dto.order.OrderListResponse;
import org.hhplus.hhecommerce.domain.order.Order;
import org.hhplus.hhecommerce.domain.order.OrderItem;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.order.OrderRepository;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductStatus;
import org.hhplus.hhecommerce.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOrdersUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private GetOrdersUseCase getOrdersUseCase;

    @Test
    @DisplayName("사용자의 주문 목록을 조회할 수 있다")
    void 사용자의_주문_목록을_조회할_수_있다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");

        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 50000, 10);

        OrderItem item = new OrderItem(option, 2, 50000);
        Order order = Order.create(user, List.of(item), 0);
        order.setId(1L);

        when(orderRepository.findByUserId(1L)).thenReturn(List.of(order));

        // When
        OrderListResponse response = getOrdersUseCase.execute(user.getId());

        // Then
        assertThat(response).isNotNull();
        assertThat(response.orders()).hasSize(1);
        assertThat(response.total()).isEqualTo(1);
    }
}
