package org.hhplus.hhecommerce.domain.product;

import lombok.Getter;
import lombok.Setter;
import org.hhplus.hhecommerce.domain.common.BaseTimeEntity;
import org.hhplus.hhecommerce.domain.product.exception.ProductErrorCode;
import org.hhplus.hhecommerce.domain.product.exception.ProductException;

@Getter
public class ProductOption extends BaseTimeEntity {

    @Setter
    private Long id;
    private Product product;
    private String optionName;
    private String optionValue;
    private int price;
    private int stock;

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
}
