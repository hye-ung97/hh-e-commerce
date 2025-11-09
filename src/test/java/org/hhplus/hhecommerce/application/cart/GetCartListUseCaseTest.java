package org.hhplus.hhecommerce.application.cart;

import org.hhplus.hhecommerce.api.dto.cart.CartListResponse;
import org.hhplus.hhecommerce.domain.cart.Cart;
import org.hhplus.hhecommerce.domain.cart.CartRepository;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.hhplus.hhecommerce.domain.product.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GetCartListUseCaseTest {

    private GetCartListUseCase getCartListUseCase;
    private TestCartRepository cartRepository;
    private TestProductOptionRepository productOptionRepository;

    @BeforeEach
    void setUp() {
        cartRepository = new TestCartRepository();
        productOptionRepository = new TestProductOptionRepository();
        getCartListUseCase = new GetCartListUseCase(cartRepository, productOptionRepository);
    }

    @Test
    @DisplayName("정상적으로 장바구니 목록을 조회할 수 있다")
    void 정상적으로_장바구니_목록을_조회할_수_있다() {
        // Given
        Long userId = 1L;
        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 1500000, 10);
        productOptionRepository.save(option);

        Cart cart = new Cart(userId, option.getId(), 2);
        cartRepository.save(cart);

        // When
        CartListResponse response = getCartListUseCase.execute(userId, 0, 10);

        // Then
        assertNotNull(response);
        assertEquals(1, response.items().size());
        assertEquals(1, response.totalCount());
        assertEquals(3000000, response.totalAmount()); // 1500000 * 2
    }

    @Test
    @DisplayName("빈 장바구니를 조회할 수 있다")
    void 빈_장바구니를_조회할_수_있다() {
        // Given
        Long userId = 1L;

        // When
        CartListResponse response = getCartListUseCase.execute(userId, 0, 10);

        // Then
        assertNotNull(response);
        assertEquals(0, response.items().size());
        assertEquals(0, response.totalCount());
        assertEquals(0, response.totalAmount());
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
        productOptionRepository.save(option1);
        productOptionRepository.save(option2);

        Cart cart1 = new Cart(userId, option1.getId(), 2);
        Cart cart2 = new Cart(userId, option2.getId(), 3);
        cartRepository.save(cart1);
        cartRepository.save(cart2);

        // When
        CartListResponse response = getCartListUseCase.execute(userId, 0, 10);

        // Then
        assertEquals(2, response.items().size());
        assertEquals(2, response.totalCount());
        assertEquals(3300000, response.totalAmount()); // (1500000*2) + (100000*3)
    }

    // 테스트 전용 Mock Repository
    static class TestCartRepository implements CartRepository {
        private final Map<Long, Cart> store = new HashMap<>();
        private final Map<String, Cart> userProductStore = new HashMap<>();
        private Long idCounter = 1L;

        @Override
        public Cart save(Cart cart) {
            if (cart.getId() == null) {
                cart.setId(idCounter++);
            }
            store.put(cart.getId(), cart);
            String key = cart.getUserId() + "_" + cart.getProductOptionId();
            userProductStore.put(key, cart);
            return cart;
        }

        @Override
        public Optional<Cart> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Cart> findByUserId(Long userId) {
            return store.values().stream()
                    .filter(c -> c.getUserId().equals(userId))
                    .collect(Collectors.toList());
        }

        @Override
        public List<Cart> findByUserId(Long userId, int page, int size) {
            return findByUserId(userId).stream()
                    .skip((long) page * size)
                    .limit(size)
                    .collect(Collectors.toList());
        }

        @Override
        public int countByUserId(Long userId) {
            return (int) store.values().stream()
                    .filter(c -> c.getUserId().equals(userId))
                    .count();
        }

        @Override
        public Optional<Cart> findByUserIdAndProductOptionId(Long userId, Long productOptionId) {
            String key = userId + "_" + productOptionId;
            return Optional.ofNullable(userProductStore.get(key));
        }

        @Override
        public void delete(Cart cart) {
            store.remove(cart.getId());
            String key = cart.getUserId() + "_" + cart.getProductOptionId();
            userProductStore.remove(key);
        }

        @Override
        public void deleteAllByUserId(Long userId) {
            List<Cart> userCarts = findByUserId(userId);
            userCarts.forEach(this::delete);
        }
    }

    static class TestProductOptionRepository implements ProductOptionRepository {
        private final Map<Long, ProductOption> store = new HashMap<>();

        @Override
        public ProductOption save(ProductOption productOption) {
            store.put(productOption.getId(), productOption);
            return productOption;
        }

        @Override
        public Optional<ProductOption> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<ProductOption> findByProductId(Long productId) {
            return store.values().stream()
                    .filter(po -> po.getProduct().getId().equals(productId))
                    .collect(Collectors.toList());
        }

        @Override
        public List<ProductOption> findAll() {
            return new ArrayList<>();
        }

        @Override
        public void delete(ProductOption productOption) {
        }
    }
}
