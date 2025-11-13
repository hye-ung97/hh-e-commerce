package org.hhplus.hhecommerce.domain.product;

import jakarta.persistence.*;
import lombok.Getter;
import org.hhplus.hhecommerce.domain.common.BaseTimeEntity;
import org.hhplus.hhecommerce.domain.product.exception.ProductErrorCode;
import org.hhplus.hhecommerce.domain.product.exception.ProductException;

@Getter
@Entity
@Table(name = "PRODUCT_OPTION", indexes = {
    @Index(name = "idx_product_id", columnList = "product_id"),
    @Index(name = "idx_stock", columnList = "stock")
})
public class ProductOption extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Product product;

    @Column(name = "option_name", nullable = false, length = 100)
    private String optionName;

    @Column(name = "option_value", nullable = false, length = 100)
    private String optionValue;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int stock;

    @Version
    private Long version;

    protected ProductOption() {
        super();
    }

    public ProductOption(Long id, Product product, String optionName, String optionValue, int price, int stock) {
        super();
        this.id = id;
        this.product = product;
        this.optionName = optionName;
        this.optionValue = optionValue;
        this.price = price;
        this.stock = stock;
    }

    public ProductOption(Product product, String optionName, String optionValue, int price, int stock) {
        super();
        this.product = product;
        this.optionName = optionName;
        this.optionValue = optionValue;
        this.price = price;
        this.stock = stock;
    }

    public boolean hasStock(int quantity) {
        return stock >= quantity;
    }

    public void reduceStock(int quantity) {
        if (quantity <= 0) {
            throw new ProductException(ProductErrorCode.INVALID_DEDUCT_QUANTITY);
        }

        if (!hasStock(quantity)) {
            throw new ProductException(ProductErrorCode.INSUFFICIENT_STOCK);
        }

        this.stock -= quantity;
        updateTimestamp();
    }

    public void restoreStock(int quantity) {
        if (quantity <= 0) {
            throw new ProductException(ProductErrorCode.INVALID_RESTORE_QUANTITY);
        }

        this.stock += quantity;
        updateTimestamp();
    }


    public int calculateTotalPrice(int quantity) {
        if (quantity <= 0) {
            throw new ProductException(ProductErrorCode.INVALID_QUANTITY);
        }
        return price * quantity;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
