package org.hhplus.hhecommerce.application.order;

import org.hhplus.hhecommerce.api.dto.order.OrderDetailResponse;
import org.hhplus.hhecommerce.domain.order.Order;
import org.hhplus.hhecommerce.domain.order.OrderItem;
import org.hhplus.hhecommerce.domain.order.exception.OrderException;
import org.hhplus.hhecommerce.domain.order.OrderRepository;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
import org.hhplus.hhecommerce.domain.product.ProductStatus;
import org.hhplus.hhecommerce.domain.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetOrderDetailUseCaseTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductOptionRepository productOptionRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private GetOrderDetailUseCase getOrderDetailUseCase;

    @Test
    @DisplayName("주문 상세를 조회할 수 있다")
    void 주문_상세를_조회할_수_있다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");

        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product.getId(), "RAM", "16GB", 50000, 10);

        OrderItem item = new OrderItem(option.getId(), 2, 50000);
        Order order = Order.create(user.getId(), List.of(item), 0);
        order.setId(1L);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(productOptionRepository.findById(option.getId())).thenReturn(Optional.of(option));
        when(productRepository.findById(product.getId())).thenReturn(Optional.of(product));

        // When
        OrderDetailResponse response = getOrderDetailUseCase.execute(order.getId());

        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(order.getId());
        assertThat(response.userId()).isEqualTo(user.getId());
        assertThat(response.totalAmount()).isEqualTo(100000);
        assertThat(response.items()).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 주문을 조회하면 예외가 발생한다")
    void 존재하지_않는_주문을_조회하면_예외가_발생한다() {
        // Given
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> getOrderDetailUseCase.execute(999L))
            .isInstanceOf(OrderException.class);
    }
}
