package org.hhplus.hhecommerce.application.product;

import org.hhplus.hhecommerce.api.dto.product.PopularProductsResponse;
import org.hhplus.hhecommerce.domain.order.Order;
import org.hhplus.hhecommerce.domain.order.OrderItem;
import org.hhplus.hhecommerce.domain.order.OrderRepository;
import org.hhplus.hhecommerce.domain.product.*;
import org.hhplus.hhecommerce.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GetPopularProductsUseCaseTest {

    private GetPopularProductsUseCase getPopularProductsUseCase;
    private TestProductRepository productRepository;
    private TestOrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        productRepository = new TestProductRepository();
        orderRepository = new TestOrderRepository();
        getPopularProductsUseCase = new GetPopularProductsUseCase(productRepository, orderRepository);
    }

    @Test
    @DisplayName("주문이 없을 때 최근 등록된 상품을 반환한다")
    void 주문이_없을_때_최근_등록된_상품을_반환한다() {
        // Given
        for (int i = 0; i < 3; i++) {
            Product product = new Product("상품" + i, "설명" + i, "전자제품");
            productRepository.save(product);
        }

        // When
        PopularProductsResponse response = getPopularProductsUseCase.execute();

        // Then
        assertAll("주문 없을 때 응답 검증",
            () -> assertNotNull(response),
            () -> assertEquals(3, response.totalCount()),
            () -> assertEquals(3, response.products().size()),
            () -> assertTrue(response.products().stream()
                    .allMatch(product -> product.totalSales() == 0)) // 판매량은 모두 0이어야 함
        );
    }

    @Test
    @DisplayName("판매량 기준으로 인기 상품을 정렬하여 반환한다")
    void 판매량_기준으로_인기_상품을_정렬하여_반환한다() {
        // Given
        User user = new User("테스트유저", "test@example.com");
        user.setId(1L);

        // 상품 3개 생성
        Product product1 = new Product("상품1", "설명1", "전자제품");
        Product product2 = new Product("상품2", "설명2", "전자제품");
        Product product3 = new Product("상품3", "설명3", "전자제품");
        productRepository.save(product1);
        productRepository.save(product2);
        productRepository.save(product3);

        // ProductOption 생성
        ProductOption option1 = new ProductOption(product1, "기본", "기본", 10000, 100);
        ProductOption option2 = new ProductOption(product2, "기본", "기본", 20000, 100);
        ProductOption option3 = new ProductOption(product3, "기본", "기본", 30000, 100);
        option1.setId(1L);
        option2.setId(2L);
        option3.setId(3L);

        // 주문 생성 (상품1: 10개, 상품2: 5개, 상품3: 15개)
        OrderItem item1 = new OrderItem(option1, 10, 10000);
        OrderItem item2 = new OrderItem(option2, 5, 20000);
        OrderItem item3 = new OrderItem(option3, 15, 30000);

        Order order1 = Order.create(user, List.of(item1), 0);
        Order order2 = Order.create(user, List.of(item2), 0);
        Order order3 = Order.create(user, List.of(item3), 0);

        orderRepository.save(order1);
        orderRepository.save(order2);
        orderRepository.save(order3);

        // When
        PopularProductsResponse response = getPopularProductsUseCase.execute();

        // Then
        assertAll("인기 상품 정렬 검증",
            () -> assertNotNull(response),
            () -> assertEquals(3, response.totalCount()),
            () -> assertEquals(3, response.products().size()),
            // 판매량 순서 확인: 상품3(15) > 상품1(10) > 상품2(5)
            () -> assertEquals(product3.getId(), response.products().get(0).productId()),
            () -> assertEquals(15, response.products().get(0).totalSales()),
            () -> assertEquals(product1.getId(), response.products().get(1).productId()),
            () -> assertEquals(10, response.products().get(1).totalSales()),
            () -> assertEquals(product2.getId(), response.products().get(2).productId()),
            () -> assertEquals(5, response.products().get(2).totalSales())
        );
    }

    @Test
    @DisplayName("최대 5개의 인기 상품만 반환한다")
    void 최대_5개의_인기_상품만_반환한다() {
        // Given
        User user = new User("테스트유저", "test@example.com");
        user.setId(1L);

        // 7개의 상품 생성
        List<Product> products = new ArrayList<>();
        List<ProductOption> options = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            Product product = new Product("상품" + i, "설명" + i, "전자제품");
            productRepository.save(product);
            products.add(product);

            ProductOption option = new ProductOption(product, "기본", "기본", 10000 * i, 100);
            option.setId((long) i);
            options.add(option);
        }

        // 각 상품마다 다른 수량으로 주문 생성
        for (int i = 0; i < 7; i++) {
            OrderItem item = new OrderItem(options.get(i), i + 1, 10000 * (i + 1));
            Order order = Order.create(user, List.of(item), 0);
            orderRepository.save(order);
        }

        // When
        PopularProductsResponse response = getPopularProductsUseCase.execute();

        // Then
        assertAll("최대 5개 제한 검증",
            () -> assertNotNull(response),
            () -> assertEquals(5, response.totalCount()), // 최대 5개만
            () -> assertEquals(5, response.products().size())
        );
    }

    // 테스트 전용 Mock OrderRepository
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
                    .filter(order -> order.getUser().getId().equals(userId))
                    .collect(Collectors.toList());
        }

        @Override
        public List<Order> findAll() {
            return new ArrayList<>(store.values());
        }
    }

    // 테스트 전용 Mock Repository
    static class TestProductRepository implements ProductRepository {
        private final Map<Long, Product> store = new HashMap<>();
        private Long idCounter = 1L;

        @Override
        public Product save(Product product) {
            if (product.getId() == null) {
                product.setId(idCounter++);
            }
            store.put(product.getId(), product);
            return product;
        }

        @Override
        public Optional<Product> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Product> findAll() {
            return new ArrayList<>(store.values());
        }

        @Override
        public List<Product> findAll(int page, int size) {
            return store.values().stream()
                    .sorted(Comparator.comparing(Product::getId))
                    .skip((long) page * size)
                    .limit(size)
                    .collect(Collectors.toList());
        }

        @Override
        public int countAll() {
            return store.size();
        }

        @Override
        public List<Product> findByCategory(String category) {
            return new ArrayList<>();
        }

        @Override
        public List<Product> findByStatus(ProductStatus status) {
            return new ArrayList<>();
        }

        @Override
        public void delete(Product product) {
        }
    }
}
