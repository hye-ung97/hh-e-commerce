package org.hhplus.hhecommerce.application.product;

import org.hhplus.hhecommerce.api.dto.product.ProductDetailResponse;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.exception.ProductException;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
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
class GetProductDetailUseCaseTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductOptionRepository productOptionRepository;

    @InjectMocks
    private GetProductDetailUseCase getProductDetailUseCase;

    @Test
    @DisplayName("정상적으로 상품 상세를 조회할 수 있다")
    void 정상적으로_상품_상세를_조회할_수_있다() {
        // Given
        Product product = new Product("테스트 상품", "테스트 설명", "전자제품");
        product.setId(1L);

        ProductOption option1 = new ProductOption(product.getId(), "색상", "블랙", 100000, 10);
        option1.setId(1L);
        ProductOption option2 = new ProductOption(product.getId(), "색상", "화이트", 100000, 5);
        option2.setId(2L);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productOptionRepository.findByProductId(1L)).thenReturn(List.of(option1, option2));

        // When
        ProductDetailResponse response = getProductDetailUseCase.execute(product.getId());

        // Then
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(product.getId());
        assertThat(response.name()).isEqualTo("테스트 상품");
        assertThat(response.category()).isEqualTo("전자제품");
        assertThat(response.options()).hasSize(2);
    }

    @Test
    @DisplayName("존재하지 않는 상품을 조회하면 예외가 발생한다")
    void 존재하지_않는_상품을_조회하면_예외가_발생한다() {
        // Given
        Long nonExistentId = 999L;

        when(productRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> getProductDetailUseCase.execute(nonExistentId))
            .isInstanceOf(ProductException.class);
    }
}
