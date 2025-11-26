package org.hhplus.hhecommerce.application.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.api.dto.order.CreateOrderRequest;
import org.hhplus.hhecommerce.api.dto.order.CreateOrderResponse;
import org.hhplus.hhecommerce.domain.order.OrderRepository;
import org.hhplus.hhecommerce.domain.order.OrderStatus;
import org.hhplus.hhecommerce.domain.order.exception.OrderErrorCode;
import org.hhplus.hhecommerce.domain.order.exception.OrderException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreateOrderUseCase {

    private final RedissonClient redissonClient;
    private final OrderTransactionService orderTransactionService;
    private final OrderRepository orderRepository;

    private static final String LOCK_KEY_PREFIX = "order:user:";
    private static final long WAIT_TIME = 10L;
    private static final long LEASE_TIME = 30L;

    public CreateOrderResponse execute(Long userId, CreateOrderRequest request) {
        if (orderRepository.existsByUserIdAndStatus(userId, OrderStatus.PENDING)) {
            log.warn("User {} already has a pending order (pre-check)", userId);
            throw new OrderException(OrderErrorCode.ORDER_IN_PROGRESS);
        }

        String lockKey = LOCK_KEY_PREFIX + userId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(WAIT_TIME, LEASE_TIME, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("Failed to acquire lock for user: {}", userId);
                throw new OrderException(OrderErrorCode.LOCK_ACQUISITION_FAILED);
            }

            log.debug("Lock acquired for user: {}", userId);

            boolean hasPendingOrder = orderRepository.existsByUserIdAndStatus(userId, OrderStatus.PENDING);
            if (hasPendingOrder) {
                log.warn("User {} already has a pending order (post-lock check)", userId);
                throw new OrderException(OrderErrorCode.ORDER_IN_PROGRESS);
            }

            return orderTransactionService.executeOrderLogic(userId, request);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted for user: {}", userId, e);
            throw new OrderException(OrderErrorCode.LOCK_INTERRUPTED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released for user: {}", userId);
            }
        }
    }
}
