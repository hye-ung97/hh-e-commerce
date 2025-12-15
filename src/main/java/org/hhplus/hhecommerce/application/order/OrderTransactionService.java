package org.hhplus.hhecommerce.application.order;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.api.dto.order.CreateOrderRequest;
import org.hhplus.hhecommerce.domain.cart.Cart;
import org.hhplus.hhecommerce.domain.cart.CartRepository;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.CouponStatus;
import org.hhplus.hhecommerce.domain.coupon.UserCoupon;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponErrorCode;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.hhplus.hhecommerce.domain.order.Order;
import org.hhplus.hhecommerce.domain.order.OrderCompletedEvent;
import org.hhplus.hhecommerce.domain.order.OrderItem;
import org.hhplus.hhecommerce.domain.order.OrderRepository;
import org.hhplus.hhecommerce.domain.order.PaymentCompletedEvent;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
import org.hhplus.hhecommerce.domain.order.exception.OrderErrorCode;
import org.hhplus.hhecommerce.domain.order.exception.OrderException;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.point.exception.PointErrorCode;
import org.hhplus.hhecommerce.domain.point.exception.PointException;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.hhplus.hhecommerce.domain.product.exception.ProductErrorCode;
import org.hhplus.hhecommerce.domain.product.exception.ProductException;
import org.hhplus.hhecommerce.domain.user.User;
import org.hhplus.hhecommerce.domain.user.UserRepository;
import org.hhplus.hhecommerce.domain.common.OutboxEvent;
import org.hhplus.hhecommerce.domain.common.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTransactionService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final PointRepository pointRepository;
    private final UserCouponRepository userCouponRepository;
    private final ProductOptionRepository productOptionRepository;
    private final ProductRepository productRepository;
    private final CouponRepository couponRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Retryable(
        retryFor = OptimisticLockException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 500, multiplier = 2)
    )
    @Transactional
    public OrderProcessResult executeOrderLogic(Long userId, CreateOrderRequest request) {
        log.debug("주문 로직 실행 시작 - userId: {}", userId);

        User user = validateAndGetUser(userId);
        List<Cart> carts = getValidCarts(userId);

        StockDeductionResult stockResult = deductStockAndBuildOrderItems(carts);

        int discountAmount = applyCouponIfPresent(request.getUserCouponId(), stockResult.totalAmount());

        Order order = createAndSaveOrder(userId, stockResult.orderItems(), discountAmount);

        deductUserPoint(userId, order.getFinalAmount());

        clearCart(userId);

        publishEvents(order, user, stockResult);

        log.info("주문 생성 성공. orderId={}, userId={}, finalAmount={}",
            order.getId(), userId, order.getFinalAmount());

        return new OrderProcessResult(order, stockResult.orderItems(), stockResult.productOptionMap());
    }

    @Recover
    public OrderProcessResult recoverFromOptimisticLock(OptimisticLockException e, Long userId, CreateOrderRequest request) {
        log.error("낙관적 락 재시도 실패 - userId: {}, 최대 재시도 횟수 초과", userId, e);
        throw new OrderException(OrderErrorCode.ORDER_CONFLICT);
    }


    private User validateAndGetUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new OrderException(OrderErrorCode.USER_NOT_FOUND));
    }

    private List<Cart> getValidCarts(Long userId) {
        List<Cart> carts = cartRepository.findByUserId(userId);
        if (carts.isEmpty()) {
            throw new OrderException(OrderErrorCode.EMPTY_CART);
        }
        return carts;
    }

    private StockDeductionResult deductStockAndBuildOrderItems(List<Cart> carts) {
        List<OrderItem> orderItems = new ArrayList<>();
        Map<Long, ProductOption> productOptionMap = new HashMap<>();
        int totalAmount = 0;

        for (Cart cart : carts) {
            ProductOption option = productOptionRepository.findById(cart.getProductOptionId())
                    .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_OPTION_NOT_FOUND));

            productOptionMap.put(option.getId(), option);

            int updatedRows = productOptionRepository.decreaseStock(option.getId(), cart.getQuantity());
            if (updatedRows == 0) {
                throw new ProductException(ProductErrorCode.INSUFFICIENT_STOCK);
            }

            int unitPrice = option.getPrice();
            OrderItem orderItem = new OrderItem(option.getId(), cart.getQuantity(), unitPrice);
            orderItems.add(orderItem);
            totalAmount += unitPrice * cart.getQuantity();
        }

        Set<Long> productIds = productOptionMap.values().stream()
                .map(ProductOption::getProductId)
                .collect(Collectors.toSet());
        Map<Long, Product> productMap = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        return new StockDeductionResult(orderItems, productOptionMap, productMap, totalAmount);
    }

    private int applyCouponIfPresent(Long userCouponId, int totalAmount) {
        if (userCouponId == null) {
            return 0;
        }

        UserCoupon userCoupon = userCouponRepository.findById(userCouponId)
                .orElseThrow(() -> new CouponException(CouponErrorCode.USER_COUPON_NOT_FOUND));

        if (userCoupon.getStatus() != CouponStatus.AVAILABLE) {
            throw new CouponException(CouponErrorCode.COUPON_UNAVAILABLE);
        }

        Coupon coupon = couponRepository.findById(userCoupon.getCouponId())
                .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

        if (totalAmount < coupon.getMinOrderAmount()) {
            throw new CouponException(CouponErrorCode.MIN_ORDER_AMOUNT_NOT_MET);
        }

        int discountAmount = coupon.calculateDiscount(totalAmount);

        int updatedRows = userCouponRepository.useCoupon(userCoupon.getId());
        if (updatedRows == 0) {
            throw new CouponException(CouponErrorCode.COUPON_ALREADY_USED);
        }

        return discountAmount;
    }

    private Order createAndSaveOrder(Long userId, List<OrderItem> orderItems, int discountAmount) {
        Order order = Order.create(userId, orderItems, discountAmount);
        return orderRepository.save(order);
    }

    private void deductUserPoint(Long userId, int amount) {
        if (pointRepository.findByUserId(userId).isEmpty()) {
            throw new PointException(PointErrorCode.POINT_NOT_FOUND);
        }

        int updatedRows = pointRepository.deductPoint(userId, amount);
        if (updatedRows == 0) {
            throw new PointException(PointErrorCode.INSUFFICIENT_BALANCE);
        }
    }

    private void clearCart(Long userId) {
        cartRepository.deleteAllByUserId(userId);
    }

    private void publishEvents(Order order, User user, StockDeductionResult stockResult) {
        Map<Long, Integer> productQuantityMap = buildProductQuantityMap(
                stockResult.orderItems(), stockResult.productOptionMap());

        if (!productQuantityMap.isEmpty()) {
            OrderCompletedEvent orderEvent = new OrderCompletedEvent(order.getId(), productQuantityMap);
            saveToOutbox("Order", order.getId(), "OrderCompletedEvent", orderEvent);
            log.debug("주문 완료 이벤트 Outbox 저장 - orderId: {}, products: {}", order.getId(), productQuantityMap.size());
        }

        PaymentCompletedEvent paymentEvent = buildPaymentCompletedEvent(
                order, user, stockResult, productQuantityMap);
        saveToOutbox("Order", order.getId(), "PaymentCompletedEvent", paymentEvent);
        log.debug("결제 완료 이벤트 Outbox 저장 - orderId: {}, items: {}",
                order.getId(), paymentEvent.orderItems().size());
    }

    private void saveToOutbox(String aggregateType, Long aggregateId, String eventType, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = new OutboxEvent(aggregateType, aggregateId, eventType, payload);
            outboxEventRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            log.error("Outbox 이벤트 직렬화 실패 - eventType: {}, aggregateId: {}", eventType, aggregateId, e);
            throw new RuntimeException("Failed to serialize event for outbox", e);
        }
    }

    private Map<Long, Integer> buildProductQuantityMap(List<OrderItem> orderItems,
                                                        Map<Long, ProductOption> productOptionMap) {
        Map<Long, Integer> productQuantityMap = orderItems.stream()
                .collect(Collectors.groupingBy(
                        item -> {
                            ProductOption option = productOptionMap.get(item.getProductOptionId());
                            return option != null ? option.getProductId() : -1L;
                        },
                        Collectors.summingInt(OrderItem::getQuantity)
                ));
        productQuantityMap.remove(-1L);
        return productQuantityMap;
    }

    private PaymentCompletedEvent buildPaymentCompletedEvent(Order order, User user,
                                                              StockDeductionResult stockResult,
                                                              Map<Long, Integer> productQuantityMap) {
        List<PaymentCompletedEvent.OrderItemInfo> orderItemInfos = stockResult.orderItems().stream()
                .map(item -> {
                    ProductOption option = stockResult.productOptionMap().get(item.getProductOptionId());
                    Product product = option != null ? stockResult.productMap().get(option.getProductId()) : null;
                    return new PaymentCompletedEvent.OrderItemInfo(
                            product != null ? product.getId() : null,
                            product != null ? product.getName() : "알 수 없는 상품",
                            option != null ? option.getOptionName() : "",
                            item.getQuantity(),
                            item.getUnitPrice(),
                            item.getSubTotal()
                    );
                })
                .toList();

        return PaymentCompletedEvent.of(
                order.getId(),
                order.getUserId(),
                user.getPhone(),
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getFinalAmount(),
                orderItemInfos,
                productQuantityMap,
                order.getOrderedAt()
        );
    }

    private record StockDeductionResult(
            List<OrderItem> orderItems,
            Map<Long, ProductOption> productOptionMap,
            Map<Long, Product> productMap,
            int totalAmount
    ) {}
}
