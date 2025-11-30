package org.hhplus.hhecommerce.application.order;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
public class CreateOrderUseCase {

    private final RedissonClient redissonClient;
    private final OrderTransactionService orderTransactionService;
    private final OrderRepository orderRepository;
    private final Timer lockWaitTimer;
    private final Timer lockHoldTimer;
    private final Counter lockAcquiredCounter;
    private final Counter lockFailedCounter;

    private static final String LOCK_KEY_PREFIX = "order:user:";
    private static final long WAIT_TIME = 10L;
    private static final long LEASE_TIME = 30L;

    public CreateOrderUseCase(RedissonClient redissonClient,
                               OrderTransactionService orderTransactionService,
                               OrderRepository orderRepository,
                               MeterRegistry meterRegistry) {
        this.redissonClient = redissonClient;
        this.orderTransactionService = orderTransactionService;
        this.orderRepository = orderRepository;

        this.lockWaitTimer = Timer.builder("order.lock.wait.time")
                .description("주문 락 획득 대기 시간")
                .tag("type", "order")
                .register(meterRegistry);

        this.lockHoldTimer = Timer.builder("order.lock.hold.time")
                .description("주문 락 점유 시간")
                .tag("type", "order")
                .register(meterRegistry);

        this.lockAcquiredCounter = Counter.builder("order.lock.acquired")
                .description("주문 락 획득 성공 횟수")
                .tag("type", "order")
                .register(meterRegistry);

        this.lockFailedCounter = Counter.builder("order.lock.failed")
                .description("주문 락 획득 실패 횟수")
                .tag("type", "order")
                .register(meterRegistry);
    }

    public CreateOrderResponse execute(Long userId, CreateOrderRequest request) {
        if (orderRepository.existsByUserIdAndStatus(userId, OrderStatus.PENDING)) {
            log.warn("User {} already has a pending order (pre-check)", userId);
            throw new OrderException(OrderErrorCode.ORDER_IN_PROGRESS);
        }

        String lockKey = LOCK_KEY_PREFIX + userId;
        RLock lock = redissonClient.getLock(lockKey);
        long waitStartTime = System.nanoTime();

        try {
            boolean acquired = lock.tryLock(WAIT_TIME, LEASE_TIME, TimeUnit.SECONDS);
            long waitDuration = System.nanoTime() - waitStartTime;
            lockWaitTimer.record(waitDuration, TimeUnit.NANOSECONDS);

            if (!acquired) {
                lockFailedCounter.increment();
                log.warn("Failed to acquire lock for user: {}, waitTime: {}ms",
                        userId, TimeUnit.NANOSECONDS.toMillis(waitDuration));
                throw new OrderException(OrderErrorCode.LOCK_ACQUISITION_FAILED);
            }

            lockAcquiredCounter.increment();
            log.debug("Lock acquired for user: {}, waitTime: {}ms",
                    userId, TimeUnit.NANOSECONDS.toMillis(waitDuration));

            long holdStartTime = System.nanoTime();
            try {
                boolean hasPendingOrder = orderRepository.existsByUserIdAndStatus(userId, OrderStatus.PENDING);
                if (hasPendingOrder) {
                    log.warn("User {} already has a pending order (post-lock check)", userId);
                    throw new OrderException(OrderErrorCode.ORDER_IN_PROGRESS);
                }

                return orderTransactionService.executeOrderLogic(userId, request);
            } finally {
                long holdDuration = System.nanoTime() - holdStartTime;
                lockHoldTimer.record(holdDuration, TimeUnit.NANOSECONDS);
                log.debug("Lock hold time for user: {}, holdTime: {}ms",
                        userId, TimeUnit.NANOSECONDS.toMillis(holdDuration));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lockFailedCounter.increment();
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
