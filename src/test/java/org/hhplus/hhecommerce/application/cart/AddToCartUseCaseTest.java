package org.hhplus.hhecommerce.application.cart;

import org.hhplus.hhecommerce.api.dto.cart.AddCartRequest;
import org.hhplus.hhecommerce.api.dto.cart.CartItemResponse;
import org.hhplus.hhecommerce.domain.cart.Cart;
import org.hhplus.hhecommerce.domain.cart.CartRepository;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.hhplus.hhecommerce.domain.product.ProductStatus;
import org.hhplus.hhecommerce.domain.product.exception.ProductException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AddToCartUseCaseTest {

    private AddToCartUseCase addToCartUseCase;
    private TestCartRepository cartRepository;
    private TestProductOptionRepository productOptionRepository;

    @BeforeEach
    void setUp() {
        cartRepository = new TestCartRepository();
        productOptionRepository = new TestProductOptionRepository();
        addToCartUseCase = new AddToCartUseCase(cartRepository, productOptionRepository);
    }

    @Test
    @DisplayName("정상적으로 장바구니에 상품을 추가할 수 있다")
    void 정상적으로_장바구니에_상품을_추가할_수_있다() {
        // Given
        Long userId = 1L;
        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 1500000, 10);
        productOptionRepository.save(option);

        AddCartRequest request = new AddCartRequest(option.getId(), 2);

        // When
        CartItemResponse response = addToCartUseCase.execute(userId, request);

        // Then
        assertNotNull(response);
        assertEquals(userId, response.getUserId());
        assertEquals(option.getId(), response.getProductOptionId());
        assertEquals(2, response.getQuantity());
        assertEquals(3000000, response.getTotalPrice());
    }

    @Test
    @DisplayName("이미 장바구니에 있는 상품을 추가하면 수량이 증가한다")
    void 이미_장바구니에_있는_상품을_추가하면_수량이_증가한다() {
        // Given
        Long userId = 1L;
        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 1500000, 10);
        productOptionRepository.save(option);

        Cart cart = new Cart(userId, option.getId(), 2);
        cartRepository.save(cart);

        AddCartRequest request = new AddCartRequest(option.getId(), 3);

        // When
        CartItemResponse response = addToCartUseCase.execute(userId, request);

        // Then
        assertEquals(5, response.getQuantity()); // 2 + 3
        assertEquals(7500000, response.getTotalPrice()); // 1500000 * 5
    }

    @Test
    @DisplayName("재고가 부족한 상품은 장바구니에 추가할 수 없다")
    void 재고가_부족한_상품은_장바구니에_추가할_수_없다() {
        // Given
        Long userId = 1L;
        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 1500000, 5);
        productOptionRepository.save(option);

        AddCartRequest request = new AddCartRequest(option.getId(), 10);

        // When & Then
        assertThrows(ProductException.class, () -> {
            addToCartUseCase.execute(userId, request);
        });
    }

    @Test
    @DisplayName("존재하지 않는 상품은 장바구니에 추가할 수 없다")
    void 존재하지_않는_상품은_장바구니에_추가할_수_없다() {
        // Given
        Long userId = 1L;
        AddCartRequest request = new AddCartRequest(999L, 2);

        // When & Then
        assertThrows(ProductException.class, () -> {
            addToCartUseCase.execute(userId, request);
        });
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
