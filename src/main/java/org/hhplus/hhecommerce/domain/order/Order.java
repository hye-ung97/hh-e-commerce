package org.hhplus.hhecommerce.domain.order;

import jakarta.persistence.*;
import lombok.Getter;
import org.hhplus.hhecommerce.domain.common.BaseTimeEntity;
import java.time.LocalDateTime;
import java.util.*;

@Getter
@Entity
@Table(name = "`ORDER`", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_created_at_status", columnList = "created_at,status")
})
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private final List<OrderItem> orderItems = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    @Column(name = "discount_amount", nullable = false)
    private int discountAmount;

    @Column(name = "final_amount", nullable = false)
    private int finalAmount;

    @Column(name = "ordered_at", nullable = false)
    private LocalDateTime orderedAt;

    protected Order() { super(); }

    public static Order create(Long userId, List<OrderItem> items, int discountAmount) {
        Order order = new Order();
        order.userId = userId;
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
