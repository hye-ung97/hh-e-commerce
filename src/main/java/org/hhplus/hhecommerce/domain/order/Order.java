package org.hhplus.hhecommerce.domain.order;

import lombok.Getter;
import org.hhplus.hhecommerce.domain.common.BaseTimeEntity;
import org.hhplus.hhecommerce.domain.user.User;
import java.time.LocalDateTime;
import java.util.*;

@Getter
public class Order extends BaseTimeEntity {
    private Long id;
    private User user;
    private final List<OrderItem> orderItems = new ArrayList<>();
    private OrderStatus status;
    private int totalAmount;
    private int discountAmount;
    private int finalAmount;
    private LocalDateTime orderedAt;

    protected Order() { super(); }

    public static Order create(User user, List<OrderItem> items, int discountAmount) {
        Order order = new Order();
        order.user = user;
        order.status = OrderStatus.PENDING;
        order.orderedAt = LocalDateTime.now();
        order.discountAmount = discountAmount;
        for (OrderItem item : items) {
            order.addOrderItem(item);
        }
        order.calculateTotalAmount();
        order.calculateFinalAmount();
        return order;
    }

    private void addOrderItem(OrderItem item) {
        orderItems.add(item);
        item.setOrder(this);
    }

    private void calculateTotalAmount() {
        this.totalAmount = orderItems.stream()
                .mapToInt(OrderItem::getTotalPrice)
                .sum();
    }

    private void calculateFinalAmount() {
        this.finalAmount = this.totalAmount - this.discountAmount;
    }

    public void confirm() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("대기 상태만 확정할 수 있습니다");
        }
        this.status = OrderStatus.CONFIRMED;
        updateTimestamp();
    }

    public void cancel() {
        if (status == OrderStatus.COMPLETED || status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("완료/취소된 주문은 취소할 수 없습니다");
        }
        this.status = OrderStatus.CANCELLED;
        updateTimestamp();
    }

    public void complete() {
        if (status != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("확정된 주문만 완료할 수 있습니다");
        }
        this.status = OrderStatus.COMPLETED;
        updateTimestamp();
    }

    public void setId(Long id) {
        this.id = id;
    }
}
