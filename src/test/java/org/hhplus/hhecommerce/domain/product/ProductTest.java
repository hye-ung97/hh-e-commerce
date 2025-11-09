package org.hhplus.hhecommerce.domain.product;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
        assertAll("상품 생성 검증",
            () -> assertNotNull(newProduct),
            () -> assertEquals("키보드", newProduct.getName()),
            () -> assertEquals("기계식 키보드", newProduct.getDescription()),
            () -> assertEquals("전자제품", newProduct.getCategory()),
            () -> assertEquals(ProductStatus.ACTIVE, newProduct.getStatus()) // 기본값은 ACTIVE
        );
    }

    @Test
    @DisplayName("상품을 활성화할 수 있다")
    void 상품을_활성화할_수_있다() {
        // Given
        product.deactivate();

        // When
        product.activate();

        // Then
        assertAll("상품 활성화 검증",
            () -> assertEquals(ProductStatus.ACTIVE, product.getStatus()),
            () -> assertTrue(product.isActive())
        );
    }

    @Test
    @DisplayName("상품을 비활성화할 수 있다")
    void 상품을_비활성화할_수_있다() {
        // When
        product.deactivate();

        // Then
        assertAll("상품 비활성화 검증",
            () -> assertEquals(ProductStatus.INACTIVE, product.getStatus()),
            () -> assertFalse(product.isActive())
        );
    }

    @Test
    @DisplayName("활성 상태를 확인할 수 있다")
    void 활성_상태를_확인할_수_있다() {
        // When & Then
        assertTrue(product.isActive());

        product.deactivate();
        assertFalse(product.isActive());
    }

    @Test
    @DisplayName("상품 상태를 여러 번 변경할 수 있다")
    void 상품_상태를_여러_번_변경할_수_있다() {
        // Given
        assertTrue(product.isActive());

        // When
        product.deactivate();
        // Then
        assertFalse(product.isActive());

        // When
        product.activate();
        // Then
        assertTrue(product.isActive());

        // When
        product.deactivate();
        // Then
        assertFalse(product.isActive());
    }

    @Test
    @DisplayName("새로 생성된 상품은 기본적으로 활성 상태다")
    void 새로_생성된_상품은_기본적으로_활성_상태다() {
        // Given & When
        Product newProduct = new Product("마우스", "무선 마우스", "전자제품");

        // Then
        assertAll("새 상품 기본 상태 검증",
            () -> assertEquals(ProductStatus.ACTIVE, newProduct.getStatus()),
            () -> assertTrue(newProduct.isActive())
        );
    }

    @Test
    @DisplayName("ID를 포함한 생성자로 상품을 생성할 수 있다")
    void ID를_포함한_생성자로_상품을_생성할_수_있다() {
        // Given & When
        Product newProduct = new Product(999L, "모니터", "4K 모니터", "전자제품", ProductStatus.INACTIVE);

        // Then
        assertAll("ID 포함 생성자 검증",
            () -> assertEquals(999L, newProduct.getId()),
            () -> assertEquals("모니터", newProduct.getName()),
            () -> assertEquals("4K 모니터", newProduct.getDescription()),
            () -> assertEquals("전자제품", newProduct.getCategory()),
            () -> assertEquals(ProductStatus.INACTIVE, newProduct.getStatus()),
            () -> assertFalse(newProduct.isActive())
        );
    }
}
