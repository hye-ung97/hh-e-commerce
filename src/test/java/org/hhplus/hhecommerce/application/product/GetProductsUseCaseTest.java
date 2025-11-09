package org.hhplus.hhecommerce.application.product;

import org.hhplus.hhecommerce.api.dto.product.ProductListResponse;
import org.hhplus.hhecommerce.domain.product.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class GetProductsUseCaseTest {

    private GetProductsUseCase getProductsUseCase;
    private TestProductRepository productRepository;
    private TestProductOptionRepository productOptionRepository;

    @BeforeEach
    void setUp() {
        productRepository = new TestProductRepository();
        productOptionRepository = new TestProductOptionRepository();
        getProductsUseCase = new GetProductsUseCase(productRepository, productOptionRepository);
    }

    @Test
    @DisplayName("정상적으로 상품 목록을 조회할 수 있다")
    void 정상적으로_상품_목록을_조회할_수_있다() {
        // Given
        Product product = new Product("테스트 상품", "테스트 설명", "전자제품");
        Product savedProduct = productRepository.save(product);

        ProductOption option = new ProductOption(savedProduct, "색상", "블랙", 100000, 10);
        productOptionRepository.save(option);

        // When
        ProductListResponse response = getProductsUseCase.execute(0, 10);

        // Then
        assertNotNull(response);
        assertTrue(response.products().size() > 0);
        assertEquals(0, response.page());
        assertEquals(10, response.size());
    }

    @Test
    @DisplayName("상품 목록 조회 시 재고와 최소 가격이 정확히 계산된다")
    void 상품_목록_조회_시_재고와_최소_가격이_정확히_계산된다() {
        // Given
        Product product = new Product("노트북", "고성능 노트북", "전자제품");
        Product savedProduct = productRepository.save(product);

        productOptionRepository.save(new ProductOption(savedProduct, "RAM", "8GB", 1000000, 5));
        productOptionRepository.save(new ProductOption(savedProduct, "RAM", "16GB", 1200000, 3));

        // When
        ProductListResponse response = getProductsUseCase.execute(0, 10);

        // Then
        ProductListResponse.ProductSummary productSummary = response.products().stream()
                .filter(p -> p.name().equals("노트북"))
                .findFirst()
                .orElseThrow();

        assertEquals(8, productSummary.stock()); // 5 + 3
        assertEquals(1000000, productSummary.price()); // 최소 가격
    }

    @Test
    @DisplayName("페이징이 정상적으로 동작한다")
    void 페이징이_정상적으로_동작한다() {
        // Given
        for (int i = 0; i < 15; i++) {
            Product product = new Product("상품" + i, "설명" + i, "전자제품");
            productRepository.save(product);
        }

        // When
        ProductListResponse page1 = getProductsUseCase.execute(0, 5);
        ProductListResponse page2 = getProductsUseCase.execute(1, 5);

        // Then
        assertEquals(5, page1.products().size());
        assertEquals(5, page2.products().size());
        assertTrue(page1.total() >= 15);
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

    static class TestProductOptionRepository implements ProductOptionRepository {
        private final Map<Long, ProductOption> store = new HashMap<>();
        private Long idCounter = 1L;

        @Override
        public ProductOption save(ProductOption productOption) {
            if (productOption.getId() == null) {
                productOption.setId(idCounter++);
            }
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
