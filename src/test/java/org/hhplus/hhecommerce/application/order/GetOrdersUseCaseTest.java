package org.hhplus.hhecommerce.application.order;

import org.hhplus.hhecommerce.api.dto.order.OrderListResponse;
import org.hhplus.hhecommerce.domain.order.Order;
import org.hhplus.hhecommerce.domain.order.OrderItem;
import org.hhplus.hhecommerce.domain.order.OrderRepository;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductStatus;
import org.hhplus.hhecommerce.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GetOrdersUseCaseTest {

    private GetOrdersUseCase getOrdersUseCase;
    private TestOrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository = new TestOrderRepository();
        getOrdersUseCase = new GetOrdersUseCase(orderRepository);
    }

    @Test
    @DisplayName("사용자의 주문 목록을 조회할 수 있다")
    void 사용자의_주문_목록을_조회할_수_있다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");

        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 50000, 10);

        OrderItem item = new OrderItem(option, 2, 50000);
        Order order = Order.create(user, List.of(item), 0);
        orderRepository.save(order);

        // When
        OrderListResponse response = getOrdersUseCase.execute(user.getId());

        // Then
        assertNotNull(response);
        assertEquals(1, response.orders().size());
        assertEquals(1, response.total());
    }

    // 테스트 전용 Mock Repository
    static class TestOrderRepository implements OrderRepository {
        private final Map<Long, Order> store = new HashMap<>();
        private Long idCounter = 1L;

        @Override
        public Order save(Order order) {
            if (order.getId() == null) {
                order.setId(idCounter++);
            }
            store.put(order.getId(), order);
            return order;
        }

        @Override
        public Optional<Order> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Order> findByUserId(Long userId) {
            return store.values().stream()
                    .filter(o -> o.getUser().getId().equals(userId))
                    .collect(Collectors.toList());
        }

        @Override
        public List<Order> findAll() {
            return new ArrayList<>(store.values());
        }
    }
}
