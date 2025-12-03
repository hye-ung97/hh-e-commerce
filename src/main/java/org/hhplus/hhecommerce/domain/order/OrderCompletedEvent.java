package org.hhplus.hhecommerce.domain.order;

import java.util.Map;

public record OrderCompletedEvent(
        Long orderId,
        Map<Long, Integer> productQuantityMap
) {
}
