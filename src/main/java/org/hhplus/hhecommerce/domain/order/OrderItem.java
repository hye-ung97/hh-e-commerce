package org.hhplus.hhecommerce.domain.order;

import lombok.Getter;
import org.hhplus.hhecommerce.domain.common.BaseTimeEntity;
import org.hhplus.hhecommerce.domain.product.ProductOption;

@Getter
public class OrderItem extends BaseTimeEntity {
    private Long id;
    private Order order;
    private ProductOption productOption;
    private int quantity;
    private int unitPrice;
    private int subTotal;
    private OrderItemStatus status;

    protected OrderItem() { super(); }

    public OrderItem(ProductOption productOption, int quantity, int unitPrice) {
        super();
        this.productOption = productOption;
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
