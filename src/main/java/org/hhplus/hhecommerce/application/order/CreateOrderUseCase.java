package org.hhplus.hhecommerce.application.order;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.api.dto.order.CreateOrderRequest;
import org.hhplus.hhecommerce.api.dto.order.CreateOrderResponse;
import org.hhplus.hhecommerce.domain.order.Order;
import org.hhplus.hhecommerce.domain.order.OrderItem;
import org.hhplus.hhecommerce.domain.order.OrderRepository;
import org.hhplus.hhecommerce.domain.order.OrderStatus;
import org.hhplus.hhecommerce.domain.order.exception.OrderErrorCode;
import org.hhplus.hhecommerce.domain.order.exception.OrderException;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
import org.hhplus.hhecommerce.domain.product.exception.ProductErrorCode;
import org.hhplus.hhecommerce.domain.product.exception.ProductException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Slf4j
@Service
public class CreateOrderUseCase {

    private final RedissonClient redissonClient;
    private final OrderTransactionService orderTransactionService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
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
                               ProductRepository productRepository,
                               MeterRegistry meterRegistry) {
        this.redissonClient = redissonClient;
        this.orderTransactionService = orderTransactionService;
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;

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

                OrderProcessResult processResult = orderTransactionService.executeOrderLogic(userId, request);

                return buildOrderResponse(userId, processResult);
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

    private CreateOrderResponse buildOrderResponse(Long userId, OrderProcessResult processResult) {
        Order order = processResult.order();
        List<OrderItem> orderItems = processResult.orderItems();
        Map<Long, ProductOption> productOptionMap = processResult.productOptionMap();

        List<Long> productIds = productOptionMap.values().stream()
                .map(ProductOption::getProductId)
                .distinct()
                .toList();

        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, product -> product));

        if (productMap.size() != productIds.size()) {
            List<Long> missingIds = productIds.stream()
                    .filter(id -> !productMap.containsKey(id))
                    .toList();
            log.error("상품 조회 실패 - 존재하지 않는 상품: {}", missingIds);
            throw new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND);
        }

        List<CreateOrderResponse.OrderItemInfo> itemInfos = orderItems.stream()
                .map(item -> {
                    ProductOption option = productOptionMap.get(item.getProductOptionId());
                    Product product = productMap.get(option.getProductId());
                    return new CreateOrderResponse.OrderItemInfo(
                            product.getName(),
                            option.getOptionName(),
                            item.getUnitPrice(),
                            item.getQuantity(),
                            item.getSubTotal()
                    );
                })
                .collect(Collectors.toList());

        return new CreateOrderResponse(
                order.getId(),
                userId,
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getFinalAmount(),
                itemInfos,
                order.getOrderedAt(),
                "주문이 완료되었습니다"
        );
    }
}
