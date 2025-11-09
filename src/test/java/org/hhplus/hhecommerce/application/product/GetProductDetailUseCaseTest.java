package org.hhplus.hhecommerce.application.product;

import org.hhplus.hhecommerce.api.dto.product.ProductDetailResponse;
import org.hhplus.hhecommerce.domain.product.*;
import org.hhplus.hhecommerce.domain.product.exception.ProductException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class GetProductDetailUseCaseTest {

    private GetProductDetailUseCase getProductDetailUseCase;
    private TestProductRepository productRepository;
    private TestProductOptionRepository productOptionRepository;

    @BeforeEach
    void setUp() {
        productRepository = new TestProductRepository();
        productOptionRepository = new TestProductOptionRepository();
        getProductDetailUseCase = new GetProductDetailUseCase(productRepository, productOptionRepository);
    }

    @Test
    @DisplayName("정상적으로 상품 상세를 조회할 수 있다")
    void 정상적으로_상품_상세를_조회할_수_있다() {
        // Given
        Product product = new Product("테스트 상품", "테스트 설명", "전자제품");
        Product savedProduct = productRepository.save(product);

        ProductOption option1 = new ProductOption(savedProduct, "색상", "블랙", 100000, 10);
        ProductOption option2 = new ProductOption(savedProduct, "색상", "화이트", 100000, 5);
        productOptionRepository.save(option1);
        productOptionRepository.save(option2);

        // When
        ProductDetailResponse response = getProductDetailUseCase.execute(savedProduct.getId());

        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(savedProduct.getId());
        assertThat(response.name()).isEqualTo("테스트 상품");
        assertThat(response.category()).isEqualTo("전자제품");
        assertThat(response.options()).hasSize(2);
    }

    @Test
    @DisplayName("존재하지 않는 상품을 조회하면 예외가 발생한다")
    void 존재하지_않는_상품을_조회하면_예외가_발생한다() {
        // Given
        Long nonExistentId = 999L;

        // When & Then
        assertThatThrownBy(() -> getProductDetailUseCase.execute(nonExistentId))
            .isInstanceOf(ProductException.class);
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
