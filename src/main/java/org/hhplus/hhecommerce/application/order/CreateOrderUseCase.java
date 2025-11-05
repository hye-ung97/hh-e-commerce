package org.hhplus.hhecommerce.application.order;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.order.CreateOrderRequest;
import org.hhplus.hhecommerce.api.dto.order.CreateOrderResponse;
import org.hhplus.hhecommerce.domain.cart.Cart;
import org.hhplus.hhecommerce.domain.cart.CartRepository;
import org.hhplus.hhecommerce.domain.coupon.*;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponErrorCode;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.hhplus.hhecommerce.domain.order.Order;
import org.hhplus.hhecommerce.domain.order.OrderItem;
import org.hhplus.hhecommerce.domain.order.OrderRepository;
import org.hhplus.hhecommerce.domain.order.exception.OrderErrorCode;
import org.hhplus.hhecommerce.domain.order.exception.OrderException;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.hhplus.hhecommerce.domain.product.exception.ProductErrorCode;
import org.hhplus.hhecommerce.domain.product.exception.ProductException;
import org.hhplus.hhecommerce.domain.user.User;
import org.hhplus.hhecommerce.domain.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    public synchronized CreateOrderResponse execute(Long userId, CreateOrderRequest request) {
        // 1. 사용자 조회 (없으면 자동 생성)
        User user = userRepository.findById(userId)
                .orElseGet(() -> {
                    User newUser = new User("사용자" + userId, "user" + userId + "@example.com");
                    newUser.setId(userId);
                    return userRepository.save(newUser);
                });

        // 2. 장바구니 조회
        List<Cart> carts = cartRepository.findByUserId(userId);
        if (carts.isEmpty()) {
            throw new OrderException(OrderErrorCode.EMPTY_CART);
        }

        // 3. 주문 항목 생성 및 재고 차감
        List<OrderItem> orderItems = new ArrayList<>();

        for (Cart cart : carts) {
            ProductOption option = productOptionRepository.findById(cart.getProductOptionId())
                    .orElseThrow(() -> new ProductException(ProductErrorCode.PRODUCT_OPTION_NOT_FOUND));

            option.reduceStock(cart.getQuantity());
            productOptionRepository.save(option);

            int unitPrice = option.getPrice();
            OrderItem orderItem = new OrderItem(option, cart.getQuantity(), unitPrice);
            orderItems.add(orderItem);
        }

        // 4. 주문 생성 (도메인 로직 사용) - 할인 전 금액 계산을 위해 먼저 생성
        Order order = Order.create(user, orderItems, 0);

        // 5. 쿠폰 적용 (선택사항)
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

        // 6. 주문에 할인 금액 적용 (재생성)
        order = Order.create(user, orderItems, discountAmount);
        orderRepository.save(order);

        // 7. 포인트 차감
        Point point = pointRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Point newPoint = new Point(user);
                    return pointRepository.save(newPoint);
                });

        point.deduct(order.getFinalAmount());
        pointRepository.save(point);

        // 8. 장바구니 비우기
        cartRepository.deleteAllByUserId(userId);

        // 9. 응답 생성
        List<CreateOrderResponse.OrderItemInfo> itemInfos = orderItems.stream()
                .map(item -> new CreateOrderResponse.OrderItemInfo(
                        item.getProductOption().getProduct().getName(),
                        item.getProductOption().getOptionName(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getSubTotal()
                ))
                .collect(Collectors.toList());

        return CreateOrderResponse.builder()
                .id(order.getId())
                .userId(user.getId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .discountAmount(order.getDiscountAmount())
                .finalAmount(order.getFinalAmount())
                .items(itemInfos)
                .createdAt(order.getOrderedAt())
                .message("주문이 완료되었습니다")
                .build();
    }
}
