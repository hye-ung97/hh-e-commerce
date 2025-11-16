package org.hhplus.hhecommerce.application.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.api.dto.order.CreateOrderRequest;
import org.hhplus.hhecommerce.api.dto.order.CreateOrderResponse;
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
import org.hhplus.hhecommerce.domain.order.OrderItem;
import org.hhplus.hhecommerce.domain.order.OrderRepository;
import org.hhplus.hhecommerce.domain.order.exception.OrderErrorCode;
import org.hhplus.hhecommerce.domain.order.exception.OrderException;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
import org.hhplus.hhecommerce.domain.product.exception.ProductErrorCode;
import org.hhplus.hhecommerce.domain.product.exception.ProductException;
import org.hhplus.hhecommerce.domain.user.User;
import org.hhplus.hhecommerce.domain.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
@RequiredArgsConstructor
public class CreateOrderUseCase {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final PointRepository pointRepository;
    private final UserCouponRepository userCouponRepository;
    private final ProductOptionRepository productOptionRepository;
    private final CouponRepository couponRepository;
    private final ProductRepository productRepository;
    private final TransactionTemplate transactionTemplate;

    public CreateOrderResponse execute(Long userId, CreateOrderRequest request) {
        return transactionTemplate.execute(status ->
            executeOrderLogic(userId, request)
        );
    }

    private CreateOrderResponse executeOrderLogic(Long userId, CreateOrderRequest request) {
        if (!userRepository.existsById(userId)) {
            User newUser = new User("사용자" + userId, "user" + userId + "@example.com");
            newUser.setId(userId);
            userRepository.save(newUser);
        }

        List<Cart> carts = cartRepository.findByUserId(userId);
        if (carts.isEmpty()) {
            throw new OrderException(OrderErrorCode.EMPTY_CART);
        }

        List<OrderItem> orderItems = new ArrayList<>();

        for (Cart cart : carts) {
            ProductOption option = productOptionRepository.findById(cart.getProductOptionId())
                    .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_OPTION_NOT_FOUND));

            option.reduceStock(cart.getQuantity());
            productOptionRepository.save(option);

            int unitPrice = option.getPrice();
            OrderItem orderItem = new OrderItem(option.getId(), cart.getQuantity(), unitPrice);
            orderItems.add(orderItem);
        }

        Order order = Order.create(userId, orderItems, 0);

        int discountAmount = 0;
        if (request.getUserCouponId() != null) {
            UserCoupon userCoupon = userCouponRepository.findById(request.getUserCouponId())
                    .orElseThrow(() -> new CouponException(CouponErrorCode.USER_COUPON_NOT_FOUND));

            if (userCoupon.getStatus() != CouponStatus.AVAILABLE) {
                throw new CouponException(CouponErrorCode.COUPON_UNAVAILABLE);
            }

            Coupon coupon = couponRepository.findById(userCoupon.getCouponId())
                    .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

            if (order.getTotalAmount() < coupon.getMinOrderAmount()) {
                throw new CouponException(CouponErrorCode.MIN_ORDER_AMOUNT_NOT_MET);
            }

            discountAmount = coupon.calculateDiscount(order.getTotalAmount());

            userCoupon.use();
            userCouponRepository.save(userCoupon);
        }

        order = Order.create(userId, orderItems, discountAmount);
        orderRepository.save(order);

        Point point = pointRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Point newPoint = new Point(userId);
                    return pointRepository.save(newPoint);
                });

        point.deduct(order.getFinalAmount());
        pointRepository.save(point);

        cartRepository.deleteAllByUserId(userId);

        Map<Long, ProductOption> productOptionMap = orderItems.stream()
                .map(OrderItem::getProductOptionId)
                .distinct()
                .collect(Collectors.toMap(
                        optionId -> optionId,
                        optionId -> productOptionRepository.findById(optionId)
                                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_OPTION_NOT_FOUND))
                ));

        Map<Long, Product> productMap = productOptionMap.values().stream()
                .map(ProductOption::getProductId)
                .distinct()
                .collect(Collectors.toMap(
                        productId -> productId,
                        productId -> productRepository.findById(productId)
                                .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_NOT_FOUND))
                ));

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

        log.info("주문 생성 성공. orderId={}, userId={}, finalAmount={}",
            order.getId(), userId, order.getFinalAmount());

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
