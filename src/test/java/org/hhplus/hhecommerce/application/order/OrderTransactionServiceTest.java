package org.hhplus.hhecommerce.application.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.OptimisticLockException;
import org.hhplus.hhecommerce.api.dto.order.CreateOrderRequest;
import org.hhplus.hhecommerce.domain.cart.Cart;
import org.hhplus.hhecommerce.domain.cart.CartRepository;
import org.hhplus.hhecommerce.domain.common.OutboxEvent;
import org.hhplus.hhecommerce.domain.common.OutboxEventRepository;
import org.hhplus.hhecommerce.domain.coupon.*;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponErrorCode;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.hhplus.hhecommerce.domain.order.Order;
import org.hhplus.hhecommerce.domain.order.OrderRepository;
import org.hhplus.hhecommerce.domain.order.exception.OrderErrorCode;
import org.hhplus.hhecommerce.domain.order.exception.OrderException;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.point.exception.PointErrorCode;
import org.hhplus.hhecommerce.domain.point.exception.PointException;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
import org.hhplus.hhecommerce.domain.product.exception.ProductErrorCode;
import org.hhplus.hhecommerce.domain.product.exception.ProductException;
import org.hhplus.hhecommerce.domain.user.User;
import org.hhplus.hhecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderTransactionService 단위 테스트")
class OrderTransactionServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PointRepository pointRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private ProductOptionRepository productOptionRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderTransactionService orderTransactionService;

    private User testUser;
    private Cart testCart;
    private ProductOption testProductOption;
    private Product testProduct;

    @BeforeEach
    void setUp() throws JsonProcessingException {
        testUser = new User("테스트유저", "test@test.com", "010-1234-5678");
        testUser.setId(1L);

        testCart = new Cart(1L, 1L, 2);
        testCart.setId(1L);

        testProductOption = new ProductOption(1L, "옵션명", "옵션A", 10000, 100);
        testProductOption.setId(1L);

        testProduct = new Product("테스트 상품", "설명", "전자제품");
        testProduct.setId(1L);

        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    @Nested
    @DisplayName("executeOrderLogic 테스트")
    class ExecuteOrderLogicTest {

        @Test
        @DisplayName("정상적으로 주문을 생성할 수 있다")
        void shouldCreateOrderSuccessfully() {
            // given
            Long userId = 1L;
            CreateOrderRequest request = new CreateOrderRequest(null);

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(cartRepository.findByUserId(userId)).thenReturn(List.of(testCart));
            when(productOptionRepository.findById(testCart.getProductOptionId()))
                    .thenReturn(Optional.of(testProductOption));
            when(productOptionRepository.decreaseStock(anyLong(), anyInt())).thenReturn(1);
            when(productRepository.findAllById(any())).thenReturn(List.of(testProduct));
            when(pointRepository.findByUserId(userId)).thenReturn(Optional.of(new Point(userId)));
            when(pointRepository.deductPoint(eq(userId), anyInt())).thenReturn(1);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(1L);
                return order;
            });

            // when
            OrderProcessResult result = orderTransactionService.executeOrderLogic(userId, request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.order()).isNotNull();
            assertThat(result.orderItems()).hasSize(1);
            verify(cartRepository).deleteAllByUserId(userId);
            verify(outboxEventRepository, atLeast(2)).save(any(OutboxEvent.class));
        }

        @Test
        @DisplayName("존재하지 않는 사용자로 주문 시 예외가 발생한다")
        void shouldThrowExceptionWhenUserNotFound() {
            // given
            Long userId = 999L;
            CreateOrderRequest request = new CreateOrderRequest(null);

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> orderTransactionService.executeOrderLogic(userId, request))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.USER_NOT_FOUND);
        }

        @Test
        @DisplayName("장바구니가 비어있으면 예외가 발생한다")
        void shouldThrowExceptionWhenCartIsEmpty() {
            // given
            Long userId = 1L;
            CreateOrderRequest request = new CreateOrderRequest(null);

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(cartRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

            // when & then
            assertThatThrownBy(() -> orderTransactionService.executeOrderLogic(userId, request))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.EMPTY_CART);
        }

        @Test
        @DisplayName("재고가 부족하면 예외가 발생한다")
        void shouldThrowExceptionWhenInsufficientStock() {
            // given
            Long userId = 1L;
            CreateOrderRequest request = new CreateOrderRequest(null);

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(cartRepository.findByUserId(userId)).thenReturn(List.of(testCart));
            when(productOptionRepository.findById(testCart.getProductOptionId()))
                    .thenReturn(Optional.of(testProductOption));
            when(productOptionRepository.decreaseStock(anyLong(), anyInt())).thenReturn(0);

            // when & then
            assertThatThrownBy(() -> orderTransactionService.executeOrderLogic(userId, request))
                    .isInstanceOf(ProductException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.INSUFFICIENT_STOCK);
        }

        @Test
        @DisplayName("상품 옵션이 존재하지 않으면 예외가 발생한다")
        void shouldThrowExceptionWhenProductOptionNotFound() {
            // given
            Long userId = 1L;
            CreateOrderRequest request = new CreateOrderRequest(null);

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(cartRepository.findByUserId(userId)).thenReturn(List.of(testCart));
            when(productOptionRepository.findById(testCart.getProductOptionId()))
                    .thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> orderTransactionService.executeOrderLogic(userId, request))
                    .isInstanceOf(ProductException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.PRODUCT_OPTION_NOT_FOUND);
        }

        @Test
        @DisplayName("포인트가 부족하면 예외가 발생한다")
        void shouldThrowExceptionWhenInsufficientPoint() {
            // given
            Long userId = 1L;
            CreateOrderRequest request = new CreateOrderRequest(null);

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(cartRepository.findByUserId(userId)).thenReturn(List.of(testCart));
            when(productOptionRepository.findById(testCart.getProductOptionId()))
                    .thenReturn(Optional.of(testProductOption));
            when(productOptionRepository.decreaseStock(anyLong(), anyInt())).thenReturn(1);
            when(productRepository.findAllById(any())).thenReturn(List.of(testProduct));
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(1L);
                return order;
            });
            when(pointRepository.findByUserId(userId)).thenReturn(Optional.of(new Point(userId)));
            when(pointRepository.deductPoint(eq(userId), anyInt())).thenReturn(0);

            // when & then
            assertThatThrownBy(() -> orderTransactionService.executeOrderLogic(userId, request))
                    .isInstanceOf(PointException.class)
                    .hasFieldOrPropertyWithValue("errorCode", PointErrorCode.INSUFFICIENT_BALANCE);
        }
    }

    @Nested
    @DisplayName("쿠폰 적용 테스트")
    class CouponApplicationTest {

        @Test
        @DisplayName("쿠폰을 적용하여 주문할 수 있다")
        void shouldApplyCouponToOrder() {
            // given
            Long userId = 1L;
            Long userCouponId = 1L;
            CreateOrderRequest request = new CreateOrderRequest(userCouponId);

            LocalDateTime now = LocalDateTime.now();
            Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 0,
                    100, now.minusDays(1), now.plusDays(30));
            coupon.setId(1L);

            UserCoupon userCoupon = new UserCoupon(userId, coupon.getId(), now.plusDays(30));
            userCoupon.setId(userCouponId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(cartRepository.findByUserId(userId)).thenReturn(List.of(testCart));
            when(productOptionRepository.findById(testCart.getProductOptionId()))
                    .thenReturn(Optional.of(testProductOption));
            when(productOptionRepository.decreaseStock(anyLong(), anyInt())).thenReturn(1);
            when(productRepository.findAllById(any())).thenReturn(List.of(testProduct));
            when(userCouponRepository.findById(userCouponId)).thenReturn(Optional.of(userCoupon));
            when(couponRepository.findById(coupon.getId())).thenReturn(Optional.of(coupon));
            when(userCouponRepository.useCoupon(userCouponId)).thenReturn(1);
            when(pointRepository.findByUserId(userId)).thenReturn(Optional.of(new Point(userId)));
            when(pointRepository.deductPoint(eq(userId), anyInt())).thenReturn(1);
            when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
                Order order = invocation.getArgument(0);
                order.setId(1L);
                return order;
            });

            // when
            OrderProcessResult result = orderTransactionService.executeOrderLogic(userId, request);

            // then
            assertThat(result).isNotNull();
            assertThat(result.order().getDiscountAmount()).isGreaterThan(0);
            verify(userCouponRepository).useCoupon(userCouponId);
        }

        @Test
        @DisplayName("존재하지 않는 쿠폰 적용 시 예외가 발생한다")
        void shouldThrowExceptionWhenUserCouponNotFound() {
            // given
            Long userId = 1L;
            Long userCouponId = 999L;
            CreateOrderRequest request = new CreateOrderRequest(userCouponId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(cartRepository.findByUserId(userId)).thenReturn(List.of(testCart));
            when(productOptionRepository.findById(testCart.getProductOptionId()))
                    .thenReturn(Optional.of(testProductOption));
            when(productOptionRepository.decreaseStock(anyLong(), anyInt())).thenReturn(1);
            when(productRepository.findAllById(any())).thenReturn(List.of(testProduct));
            when(userCouponRepository.findById(userCouponId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> orderTransactionService.executeOrderLogic(userId, request))
                    .isInstanceOf(CouponException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.USER_COUPON_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 사용한 쿠폰 적용 시 예외가 발생한다")
        void shouldThrowExceptionWhenCouponAlreadyUsed() {
            // given
            Long userId = 1L;
            Long userCouponId = 1L;
            CreateOrderRequest request = new CreateOrderRequest(userCouponId);

            LocalDateTime now = LocalDateTime.now();
            UserCoupon userCoupon = new UserCoupon(userId, 1L, now.plusDays(30));
            userCoupon.setId(userCouponId);
            // 쿠폰을 사용한 상태로 변경 (status를 USED로)
            userCoupon.use();

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(cartRepository.findByUserId(userId)).thenReturn(List.of(testCart));
            when(productOptionRepository.findById(testCart.getProductOptionId()))
                    .thenReturn(Optional.of(testProductOption));
            when(productOptionRepository.decreaseStock(anyLong(), anyInt())).thenReturn(1);
            when(productRepository.findAllById(any())).thenReturn(List.of(testProduct));
            when(userCouponRepository.findById(userCouponId)).thenReturn(Optional.of(userCoupon));

            // when & then
            assertThatThrownBy(() -> orderTransactionService.executeOrderLogic(userId, request))
                    .isInstanceOf(CouponException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_UNAVAILABLE);
        }

        @Test
        @DisplayName("최소 주문 금액 미달 시 예외가 발생한다")
        void shouldThrowExceptionWhenMinOrderAmountNotMet() {
            // given
            Long userId = 1L;
            Long userCouponId = 1L;
            CreateOrderRequest request = new CreateOrderRequest(userCouponId);

            LocalDateTime now = LocalDateTime.now();
            Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 100000,
                    100, now.minusDays(1), now.plusDays(30));
            coupon.setId(1L);

            UserCoupon userCoupon = new UserCoupon(userId, coupon.getId(), now.plusDays(30));
            userCoupon.setId(userCouponId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(testUser));
            when(cartRepository.findByUserId(userId)).thenReturn(List.of(testCart));
            when(productOptionRepository.findById(testCart.getProductOptionId()))
                    .thenReturn(Optional.of(testProductOption));
            when(productOptionRepository.decreaseStock(anyLong(), anyInt())).thenReturn(1);
            when(productRepository.findAllById(any())).thenReturn(List.of(testProduct));
            when(userCouponRepository.findById(userCouponId)).thenReturn(Optional.of(userCoupon));
            when(couponRepository.findById(coupon.getId())).thenReturn(Optional.of(coupon));

            // when & then
            assertThatThrownBy(() -> orderTransactionService.executeOrderLogic(userId, request))
                    .isInstanceOf(CouponException.class)
                    .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.MIN_ORDER_AMOUNT_NOT_MET);
        }
    }

    @Nested
    @DisplayName("recoverFromOptimisticLock 테스트")
    class RecoverFromOptimisticLockTest {

        @Test
        @DisplayName("낙관적 락 충돌 복구 시 OrderException이 발생한다")
        void shouldThrowOrderConflictException() {
            // given
            OptimisticLockException e = new OptimisticLockException("Lock conflict");
            Long userId = 1L;
            CreateOrderRequest request = new CreateOrderRequest(null);

            // when & then
            assertThatThrownBy(() -> orderTransactionService.recoverFromOptimisticLock(e, userId, request))
                    .isInstanceOf(OrderException.class)
                    .hasFieldOrPropertyWithValue("errorCode", OrderErrorCode.ORDER_CONFLICT);
        }
    }
}
