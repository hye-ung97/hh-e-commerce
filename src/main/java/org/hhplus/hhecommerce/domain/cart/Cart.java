package org.hhplus.hhecommerce.domain.cart;

import lombok.Getter;
import lombok.Setter;
import org.hhplus.hhecommerce.domain.common.BaseTimeEntity;

@Getter
public class Cart extends BaseTimeEntity {
    @Setter
    private Long id;
    private Long userId;
    private Long productOptionId;
    private int quantity;

    protected Cart() { super(); }

    public Cart(Long userId, Long productOptionId, int quantity) {
        super();
        this.userId = userId;
        this.productOptionId = productOptionId;
        this.quantity = quantity;
    }

    public void updateQuantity(int newQuantity) {
        if (newQuantity <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다");
        }
        this.quantity = newQuantity;
        updateTimestamp();
    }

    public void addQuantity(int additionalQuantity) {
        this.quantity += additionalQuantity;
        updateTimestamp();
    }
}
