package org.hhplus.hhecommerce.application.product;

import org.hhplus.hhecommerce.api.dto.product.ProductListResponse;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.infrastructure.repository.product.ProductOptionRepository;
import org.hhplus.hhecommerce.infrastructure.repository.product.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetProductsUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductOptionRepository productOptionRepository;

    @InjectMocks
    private GetProductsUseCase getProductsUseCase;

    @Test
    @DisplayName("정상적으로 상품 목록을 조회할 수 있다")
    void 정상적으로_상품_목록을_조회할_수_있다() {
        // Given
        Product product = new Product("테스트 상품", "테스트 설명", "전자제품");
        product.setId(1L);

        ProductOption option = new ProductOption(product, "색상", "블랙", 100000, 10);
        option.setId(1L);

        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(productOptionRepository.findByProductId(1L)).thenReturn(List.of(option));

        // When
        ProductListResponse response = getProductsUseCase.execute(0, 10);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.products()).hasSizeGreaterThan(0);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(10);
    }

    @Test
    @DisplayName("상품 목록 조회 시 재고와 최소 가격이 정확히 계산된다")
    void 상품_목록_조회_시_재고와_최소_가격이_정확히_계산된다() {
        // Given
        Product product = new Product("노트북", "고성능 노트북", "전자제품");
        product.setId(1L);

        ProductOption option1 = new ProductOption(product, "RAM", "8GB", 1000000, 5);
        option1.setId(1L);

        ProductOption option2 = new ProductOption(product, "RAM", "16GB", 1200000, 3);
        option2.setId(2L);

        Page<Product> page = new PageImpl<>(List.of(product));
        when(productRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(productOptionRepository.findByProductId(1L)).thenReturn(List.of(option1, option2));

        // When
        ProductListResponse response = getProductsUseCase.execute(0, 10);

        // Then
        ProductListResponse.ProductSummary productSummary = response.products().stream()
                .filter(p -> p.name().equals("노트북"))
                .findFirst()
                .orElseThrow();

        assertThat(productSummary.stock()).isEqualTo(8);
        assertThat(productSummary.price()).isEqualTo(1000000);
    }

    @Test
    @DisplayName("페이징이 정상적으로 동작한다")
    void 페이징이_정상적으로_동작한다() {
        // Given
        List<Product> products = List.of(
            createProduct(1L, "상품1"),
            createProduct(2L, "상품2"),
            createProduct(3L, "상품3"),
            createProduct(4L, "상품4"),
            createProduct(5L, "상품5")
        );

        Page<Product> page = new PageImpl<>(products, Pageable.ofSize(5), 15);
        when(productRepository.findAll(any(Pageable.class))).thenReturn(page);
        when(productRepository.count()).thenReturn(15L);
        when(productOptionRepository.findByProductId(any())).thenReturn(List.of());

        // When
        ProductListResponse page1 = getProductsUseCase.execute(0, 5);

        // Then
        assertThat(page1.products()).hasSize(5);
        assertThat(page1.total()).isEqualTo(15);
    }

    private Product createProduct(Long id, String name) {
        Product product = new Product(name, "설명" + id, "전자제품");
        product.setId(id);
        return product;
    }
}
