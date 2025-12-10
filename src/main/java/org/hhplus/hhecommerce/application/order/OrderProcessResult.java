package org.hhplus.hhecommerce.application.order;

import org.hhplus.hhecommerce.domain.order.Order;
import org.hhplus.hhecommerce.domain.order.OrderItem;
import org.hhplus.hhecommerce.domain.product.ProductOption;

import java.util.List;
import java.util.Map;

public record OrderProcessResult(
        Order order,
        List<OrderItem> orderItems,
        Map<Long, ProductOption> productOptionMap
) {}
