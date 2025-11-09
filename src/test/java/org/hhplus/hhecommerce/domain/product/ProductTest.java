package org.hhplus.hhecommerce.domain.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ProductTest {

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("상품을 생성할 수 있다")
    void 상품을_생성할_수_있다() {
        // Given & When
        Product newProduct = new Product("키보드", "기계식 키보드", "전자제품");

        // Then
        assertThat(newProduct).isNotNull();
        assertThat(newProduct.getName()).isEqualTo("키보드");
        assertThat(newProduct.getDescription()).isEqualTo("기계식 키보드");
        assertThat(newProduct.getCategory()).isEqualTo("전자제품");
        assertThat(newProduct.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("상품을 활성화할 수 있다")
    void 상품을_활성화할_수_있다() {
        // Given
        product.deactivate();

        // When
        product.activate();

        // Then
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(product.isActive()).isTrue();
    }

    @Test
    @DisplayName("상품을 비활성화할 수 있다")
    void 상품을_비활성화할_수_있다() {
        // When
        product.deactivate();

        // Then
        assertThat(product.getStatus()).isEqualTo(ProductStatus.INACTIVE);
        assertThat(product.isActive()).isFalse();
    }

    @Test
    @DisplayName("활성 상태를 확인할 수 있다")
    void 활성_상태를_확인할_수_있다() {
        // When & Then
        assertThat(product.isActive()).isTrue();

        product.deactivate();
        assertThat(product.isActive()).isFalse();
    }

    @Test
    @DisplayName("상품 상태를 여러 번 변경할 수 있다")
    void 상품_상태를_여러_번_변경할_수_있다() {
        // Given
        assertThat(product.isActive()).isTrue();

        // When
        product.deactivate();
        // Then
        assertThat(product.isActive()).isFalse();

        // When
        product.activate();
        // Then
        assertThat(product.isActive()).isTrue();

        // When
        product.deactivate();
        // Then
        assertThat(product.isActive()).isFalse();
    }

    @Test
    @DisplayName("새로 생성된 상품은 기본적으로 활성 상태다")
    void 새로_생성된_상품은_기본적으로_활성_상태다() {
        // Given & When
        Product newProduct = new Product("마우스", "무선 마우스", "전자제품");

        // Then
        assertThat(newProduct.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(newProduct.isActive()).isTrue();
    }

    @Test
    @DisplayName("ID를 포함한 생성자로 상품을 생성할 수 있다")
    void ID를_포함한_생성자로_상품을_생성할_수_있다() {
        // Given & When
        Product newProduct = new Product(999L, "모니터", "4K 모니터", "전자제품", ProductStatus.INACTIVE);

        // Then
        assertThat(newProduct.getId()).isEqualTo(999L);
        assertThat(newProduct.getName()).isEqualTo("모니터");
        assertThat(newProduct.getDescription()).isEqualTo("4K 모니터");
        assertThat(newProduct.getCategory()).isEqualTo("전자제품");
        assertThat(newProduct.getStatus()).isEqualTo(ProductStatus.INACTIVE);
        assertThat(newProduct.isActive()).isFalse();
    }
}
