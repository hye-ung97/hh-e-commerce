package org.hhplus.hhecommerce.domain.cart;

import jakarta.persistence.*;
import lombok.Getter;
import org.hhplus.hhecommerce.domain.common.BaseTimeEntity;

@Getter
@Entity
@Table(name = "CART",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_product_option", columnNames = {"user_id", "product_option_id"})
    },
    indexes = {
        @Index(name = "idx_user_id", columnList = "user_id")
    }
)
public class Cart extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_option_id", nullable = false)
    private Long productOptionId;

    @Column(nullable = false)
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

    public void setId(Long id) {
        this.id = id;
    }
}
