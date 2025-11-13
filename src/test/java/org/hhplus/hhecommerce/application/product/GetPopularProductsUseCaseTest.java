package org.hhplus.hhecommerce.application.product;

import org.hhplus.hhecommerce.api.dto.product.PopularProductsResponse;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.infrastructure.repository.order.OrderRepository;
import org.hhplus.hhecommerce.infrastructure.repository.order.PopularProductProjection;
import org.hhplus.hhecommerce.infrastructure.repository.product.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetPopularProductsUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private GetPopularProductsUseCase getPopularProductsUseCase;

    @Test
    @DisplayName("주문이 없을 때 최근 등록된 상품을 반환한다")
    void 주문이_없을_때_최근_등록된_상품을_반환한다() {
        // Given
        List<Product> products = List.of(
            createProduct(1L, "상품1"),
            createProduct(2L, "상품2"),
            createProduct(3L, "상품3")
        );

        when(orderRepository.findTopSellingProducts(any())).thenReturn(List.of());
        when(productRepository.findAll()).thenReturn(products);

        // When
        PopularProductsResponse response = getPopularProductsUseCase.execute();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.products()).hasSize(3);
        assertThat(response.products())
            .allMatch(product -> product.totalSales() == 0);
    }

    @Test
    @DisplayName("판매량 기준으로 인기 상품을 정렬하여 반환한다")
    void 판매량_기준으로_인기_상품을_정렬하여_반환한다() {
        // Given - Repository에서 정렬된 결과를 반환
        List<PopularProductProjection> projections = List.of(
            createProjection(3L, "상품3", "전자제품", "ACTIVE", 15L),
            createProjection(1L, "상품1", "전자제품", "ACTIVE", 10L),
            createProjection(2L, "상품2", "전자제품", "ACTIVE", 5L)
        );

        when(orderRepository.findTopSellingProducts(any())).thenReturn(projections);

        // When
        PopularProductsResponse response = getPopularProductsUseCase.execute();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.totalCount()).isEqualTo(3);
        assertThat(response.products()).hasSize(3);

        // 판매량 순서 확인: 상품3(15) > 상품1(10) > 상품2(5)
        assertThat(response.products().get(0).productId()).isEqualTo(3L);
        assertThat(response.products().get(0).totalSales()).isEqualTo(15);
        assertThat(response.products().get(1).productId()).isEqualTo(1L);
        assertThat(response.products().get(1).totalSales()).isEqualTo(10);
        assertThat(response.products().get(2).productId()).isEqualTo(2L);
        assertThat(response.products().get(2).totalSales()).isEqualTo(5);
    }

    @Test
    @DisplayName("최대 5개의 인기 상품만 반환한다")
    void 최대_5개의_인기_상품만_반환한다() {
        // Given - 7개 상품이 있지만 쿼리에서 5개만 반환 (LIMIT 5)
        List<PopularProductProjection> projections = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            projections.add(createProjection((long) i, "상품" + i, "전자제품", "ACTIVE", (long) (10 - i)));
        }

        when(orderRepository.findTopSellingProducts(any())).thenReturn(projections);

        // When
        PopularProductsResponse response = getPopularProductsUseCase.execute();

        // Then
        assertThat(response).isNotNull();
        assertThat(response.totalCount()).isEqualTo(5);
        assertThat(response.products()).hasSize(5);
    }

    private Product createProduct(Long id, String name) {
        Product product = new Product(name, "설명" + id, "전자제품");
        product.setId(id);
        return product;
    }

    private PopularProductProjection createProjection(Long productId, String name, String category, String status, Long totalSales) {
        return new PopularProductProjection() {
            @Override
            public Long getProductId() {
                return productId;
            }

            @Override
            public String getProductName() {
                return name;
            }

            @Override
            public String getCategory() {
                return category;
            }

            @Override
            public String getStatus() {
                return status;
            }

            @Override
            public Long getTotalSales() {
                return totalSales;
            }
        };
    }
}
