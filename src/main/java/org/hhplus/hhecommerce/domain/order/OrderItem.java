package org.hhplus.hhecommerce.domain.order;

import jakarta.persistence.*;
import lombok.Getter;
import org.hhplus.hhecommerce.domain.common.BaseTimeEntity;

@Getter
@Entity
@Table(name = "ORDER_ITEM", indexes = {
    @Index(name = "idx_order_id", columnList = "order_id"),
    @Index(name = "idx_product_option_id", columnList = "product_option_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_order_product_quantity", columnList = "order_id,product_option_id,quantity")
})
public class OrderItem extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private Order order;

    @Column(name = "product_option_id", nullable = false)
    private Long productOptionId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private int unitPrice;

    @Column(name = "sub_total", nullable = false)
    private int subTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderItemStatus status;

    protected OrderItem() { super(); }

    public OrderItem(Long productOptionId, int quantity, int unitPrice) {
        super();
        this.productOptionId = productOptionId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subTotal = unitPrice * quantity;
        this.status = OrderItemStatus.ORDERED;
    }

    public int getTotalPrice() {
        return subTotal;
    }

    public void cancel() {
        if (status == OrderItemStatus.CANCELLED) {
            throw new IllegalStateException("이미 취소된 주문 항목입니다");
        }
        this.status = OrderItemStatus.CANCELLED;
        updateTimestamp();
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setOrder(Order order) {
        this.order = order;
    }
}
