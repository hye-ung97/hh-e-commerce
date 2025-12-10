package org.hhplus.hhecommerce.domain.order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record PaymentCompletedEvent(
        int version,
        Long orderId,
        Long userId,
        String userPhone,
        int totalAmount,
        int discountAmount,
        int finalAmount,
        List<OrderItemInfo> orderItems,
        Map<Long, Integer> productQuantityMap,
        LocalDateTime orderedAt
) {
    public static final int CURRENT_VERSION = 1;

    public PaymentCompletedEvent {
        orderItems = orderItems != null ? List.copyOf(orderItems) : List.of();
        productQuantityMap = productQuantityMap != null ? Map.copyOf(productQuantityMap) : Map.of();
    }

    public static PaymentCompletedEvent of(
            Long orderId,
            Long userId,
            String userPhone,
            int totalAmount,
            int discountAmount,
            int finalAmount,
            List<OrderItemInfo> orderItems,
            Map<Long, Integer> productQuantityMap,
            LocalDateTime orderedAt
    ) {
        return new PaymentCompletedEvent(
                CURRENT_VERSION,
                orderId,
                userId,
                userPhone,
                totalAmount,
                discountAmount,
                finalAmount,
                orderItems,
                productQuantityMap,
                orderedAt
        );
    }

    public record OrderItemInfo(
            Long productId,
            String productName,
            String optionName,
            int quantity,
            int unitPrice,
            int subTotal
    ) {}
}
