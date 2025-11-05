package org.hhplus.hhecommerce.domain.product;

import org.hhplus.hhecommerce.domain.product.exception.ProductException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProductOptionTest {

    private Product product;
    private ProductOption productOption;

    @BeforeEach
    void setUp() {
        product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        productOption = new ProductOption(1L, product, "RAM", "16GB", 1500000, 10);
    }

    @Test
    @DisplayName("재고가 충분한지 확인할 수 있다")
    void 재고가_충분한지_확인할_수_있다() {
        // When & Then
        assertTrue(productOption.hasStock(5));
        assertTrue(productOption.hasStock(10));
        assertFalse(productOption.hasStock(11));
    }

    @Test
    @DisplayName("정상적으로 재고를 차감할 수 있다")
    void 정상적으로_재고를_차감할_수_있다() {
        // Given
        int initialStock = productOption.getStock();

        // When
        productOption.reduceStock(3);

        // Then
        assertEquals(initialStock - 3, productOption.getStock());
    }

    @Test
    @DisplayName("재고가 부족하면 차감할 수 없다")
    void 재고가_부족하면_차감할_수_없다() {
        // When & Then
        Exception exception = assertThrows(ProductException.class, () -> {
            productOption.reduceStock(15);
        });

        assertTrue(exception.getMessage().contains("재고"));
        assertEquals(10, productOption.getStock()); // 재고는 그대로 유지
    }

    @Test
    @DisplayName("0 이하의 수량으로 재고를 차감할 수 없다")
    void 영_이하의_수량으로_재고를_차감할_수_없다() {
        // When & Then
        assertThrows(ProductException.class, () -> {
            productOption.reduceStock(0);
        });

        assertThrows(ProductException.class, () -> {
            productOption.reduceStock(-5);
        });

        assertEquals(10, productOption.getStock()); // 재고는 그대로 유지
    }

    @Test
    @DisplayName("정상적으로 재고를 복구할 수 있다")
    void 정상적으로_재고를_복구할_수_있다() {
        // Given
        productOption.reduceStock(5);
        int currentStock = productOption.getStock();

        // When
        productOption.restoreStock(3);

        // Then
        assertEquals(currentStock + 3, productOption.getStock());
    }

    @Test
    @DisplayName("0 이하의 수량으로 재고를 복구할 수 없다")
    void 영_이하의_수량으로_재고를_복구할_수_없다() {
        // Given
        int initialStock = productOption.getStock();

        // When & Then
        assertThrows(ProductException.class, () -> {
            productOption.restoreStock(0);
        });

        assertThrows(ProductException.class, () -> {
            productOption.restoreStock(-5);
        });

        assertEquals(initialStock, productOption.getStock()); // 재고는 그대로 유지
    }

    @Test
    @DisplayName("정상적으로 총 가격을 계산할 수 있다")
    void 정상적으로_총_가격을_계산할_수_있다() {
        // When
        int totalPrice1 = productOption.calculateTotalPrice(1);
        int totalPrice2 = productOption.calculateTotalPrice(3);

        // Then
        assertEquals(1500000, totalPrice1);
        assertEquals(4500000, totalPrice2);
    }

    @Test
    @DisplayName("0 이하의 수량으로 가격을 계산할 수 없다")
    void 영_이하의_수량으로_가격을_계산할_수_없다() {
        // When & Then
        assertThrows(ProductException.class, () -> {
            productOption.calculateTotalPrice(0);
        });

        assertThrows(ProductException.class, () -> {
            productOption.calculateTotalPrice(-3);
        });
    }

    @Test
    @DisplayName("재고 차감과 복구가 연속으로 정상 동작한다")
    void 재고_차감과_복구가_연속으로_정상_동작한다() {
        // Given
        int initialStock = productOption.getStock();

        // When
        productOption.reduceStock(5);
        productOption.reduceStock(2);
        productOption.restoreStock(3);

        // Then
        assertEquals(initialStock - 4, productOption.getStock()); // 10 - 5 - 2 + 3 = 6
    }

    @Test
    @DisplayName("재고 차감 후 남은 재고로 추가 차감이 가능하다")
    void 재고_차감_후_남은_재고로_추가_차감이_가능하다() {
        // Given
        productOption.reduceStock(7); // 10 - 7 = 3

        // When
        productOption.reduceStock(3); // 3 - 3 = 0

        // Then
        assertEquals(0, productOption.getStock());
        assertFalse(productOption.hasStock(1)); // 재고 없음
    }

    @Test
    @DisplayName("재고가 0일 때 차감하면 예외가 발생한다")
    void 재고가_영일_때_차감하면_예외가_발생한다() {
        // Given
        productOption.reduceStock(10); // 재고를 모두 소진

        // When & Then
        assertThrows(ProductException.class, () -> {
            productOption.reduceStock(1);
        });
    }
}
