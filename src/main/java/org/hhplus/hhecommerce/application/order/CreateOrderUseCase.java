package org.hhplus.hhecommerce.application.order;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.order.CreateOrderRequest;
import org.hhplus.hhecommerce.api.dto.order.CreateOrderResponse;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class CreateOrderUseCase {

    private final ConcurrentHashMap<Long, Object> userLocks = new ConcurrentHashMap<>();
    private final OrderTransactionService orderTransactionService;

    public CreateOrderResponse execute(Long userId, CreateOrderRequest request) {
        Object lock = userLocks.computeIfAbsent(userId, k -> new Object());

        synchronized (lock) {
            try {
                return orderTransactionService.executeOrderLogic(userId, request);
            } finally {
                userLocks.remove(userId);
            }
        }
    }
}
