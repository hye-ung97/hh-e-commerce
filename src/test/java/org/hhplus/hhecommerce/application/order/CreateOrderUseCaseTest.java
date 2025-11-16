package org.hhplus.hhecommerce.application.order;

import org.hhplus.hhecommerce.api.dto.order.CreateOrderRequest;
import org.hhplus.hhecommerce.api.dto.order.CreateOrderResponse;
import org.hhplus.hhecommerce.domain.cart.Cart;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.cart.CartRepository;
import org.hhplus.hhecommerce.domain.coupon.CouponType;
import org.hhplus.hhecommerce.domain.coupon.UserCoupon;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.hhplus.hhecommerce.domain.order.Order;
import org.hhplus.hhecommerce.domain.order.OrderRepository;
import org.hhplus.hhecommerce.domain.order.exception.OrderException;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.point.exception.PointErrorCode;
import org.hhplus.hhecommerce.domain.point.exception.PointException;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.hhplus.hhecommerce.domain.product.ProductStatus;
import org.hhplus.hhecommerce.domain.product.exception.ProductException;
import org.hhplus.hhecommerce.domain.user.User;
import org.hhplus.hhecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateOrderUseCaseTest {

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
    private CouponRepository couponRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private CreateOrderUseCase createOrderUseCase;

    @BeforeEach
    void setUp() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    @DisplayName("정상적으로 주문을 생성할 수 있다")
    void 정상적으로_주문을_생성할_수_있다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");

        Point point = new Point(user);
        point.charge(100000);

        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 50000, 10);

        Cart cart = new Cart(user.getId(), option.getId(), 2);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(pointRepository.findByUserId(1L)).thenReturn(Optional.of(point));
        when(cartRepository.findByUserId(1L)).thenReturn(List.of(cart));
        when(productOptionRepository.findById(1L)).thenReturn(Optional.of(option));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1L);
            return order;
        });
        doNothing().when(cartRepository).deleteAllByUserId(1L);

        CreateOrderRequest request = new CreateOrderRequest(null);

        // When
        CreateOrderResponse response = createOrderUseCase.execute(user.getId(), request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.userId()).isEqualTo(user.getId());
        assertThat(response.totalAmount()).isEqualTo(100000); // 50000 * 2
        assertThat(response.discountAmount()).isEqualTo(0);
        assertThat(response.finalAmount()).isEqualTo(100000);
        assertThat(response.message()).isEqualTo("주문이 완료되었습니다");
        assertThat(response.items()).hasSize(1);
    }

    @Test
    @DisplayName("장바구니가 비어있으면 주문할 수 없다")
    void 장바구니가_비어있으면_주문할_수_없다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(List.of());

        CreateOrderRequest request = new CreateOrderRequest(null);

        // When & Then
        assertThatThrownBy(() -> createOrderUseCase.execute(user.getId(), request))
            .isInstanceOf(OrderException.class);
    }

    @Test
    @DisplayName("재고가 부족하면 주문할 수 없다")
    void 재고가_부족하면_주문할_수_없다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");

        Point point = new Point(user);
        point.charge(100000);

        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 50000, 1);

        Cart cart = new Cart(user.getId(), option.getId(), 5); // 재고보다 많은 수량

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(List.of(cart));
        when(productOptionRepository.findById(1L)).thenReturn(Optional.of(option));

        CreateOrderRequest request = new CreateOrderRequest(null);

        // When & Then
        assertThatThrownBy(() -> createOrderUseCase.execute(user.getId(), request))
            .isInstanceOf(ProductException.class);
    }

    @Test
    @DisplayName("포인트가 부족하면 주문할 수 없다")
    void 포인트가_부족하면_주문할_수_없다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");

        Point point = new Point(user);
        point.charge(10000); // 부족한 포인트

        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 50000, 10);

        Cart cart = new Cart(user.getId(), option.getId(), 2);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(pointRepository.findByUserId(1L)).thenReturn(Optional.of(point));
        when(cartRepository.findByUserId(1L)).thenReturn(List.of(cart));
        when(productOptionRepository.findById(1L)).thenReturn(Optional.of(option));

        CreateOrderRequest request = new CreateOrderRequest(null);

        // When & Then
        assertThatThrownBy(() -> createOrderUseCase.execute(user.getId(), request))
            .isInstanceOf(PointException.class)
            .hasFieldOrPropertyWithValue("errorCode", PointErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    @DisplayName("주문 완료 후 재고가 차감된다")
    void 주문_완료_후_재고가_차감된다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");

        Point point = new Point(user);
        point.charge(100000); // 충분한 포인트 충전

        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 20000, 10);

        Cart cart = new Cart(user.getId(), option.getId(), 3);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(pointRepository.findByUserId(1L)).thenReturn(Optional.of(point));
        when(cartRepository.findByUserId(1L)).thenReturn(List.of(cart));
        when(productOptionRepository.findById(1L)).thenReturn(Optional.of(option));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1L);
            return order;
        });
        doNothing().when(cartRepository).deleteAllByUserId(1L);

        CreateOrderRequest request = new CreateOrderRequest(null);

        // When
        createOrderUseCase.execute(user.getId(), request);

        // Then
        assertThat(option.getStock()).isEqualTo(7); // 10 - 3
    }

    @Test
    @DisplayName("주문 완료 후 장바구니가 비워진다")
    void 주문_완료_후_장바구니가_비워진다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");

        Point point = new Point(user);
        point.charge(100000);

        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 50000, 10);

        Cart cart = new Cart(user.getId(), option.getId(), 2);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(pointRepository.findByUserId(1L)).thenReturn(Optional.of(point));
        when(cartRepository.findByUserId(1L)).thenReturn(List.of(cart));
        when(productOptionRepository.findById(1L)).thenReturn(Optional.of(option));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1L);
            return order;
        });
        doNothing().when(cartRepository).deleteAllByUserId(1L);

        CreateOrderRequest request = new CreateOrderRequest(null);

        // When
        createOrderUseCase.execute(user.getId(), request);

        // Then
        verify(cartRepository, times(1)).deleteAllByUserId(user.getId());
    }

    @Test
    @DisplayName("쿠폰을 적용하여 주문할 수 있다")
    void 쿠폰을_적용하여_주문할_수_있다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");

        Point point = new Point(user);
        point.charge(100000);

        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 50000, 10);

        Cart cart = new Cart(user.getId(), option.getId(), 2);

        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 10000, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        coupon.setId(1L);

        UserCoupon userCoupon = new UserCoupon(user.getId(), coupon.getId(), now.plusDays(30));
        userCoupon.setId(1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(pointRepository.findByUserId(1L)).thenReturn(Optional.of(point));
        when(cartRepository.findByUserId(1L)).thenReturn(List.of(cart));
        when(productOptionRepository.findById(1L)).thenReturn(Optional.of(option));
        when(userCouponRepository.findById(1L)).thenReturn(Optional.of(userCoupon));
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(1L);
            return order;
        });
        doNothing().when(cartRepository).deleteAllByUserId(1L);

        CreateOrderRequest request = new CreateOrderRequest(userCoupon.getId());

        // When
        CreateOrderResponse response = createOrderUseCase.execute(user.getId(), request);

        // Then
        assertThat(response.totalAmount()).isEqualTo(100000); // 50000 * 2
        assertThat(response.discountAmount()).isEqualTo(10000); // 100000 * 10%
        assertThat(response.finalAmount()).isEqualTo(90000); // 100000 - 10000
    }

    @Test
    @DisplayName("최소 주문 금액을 만족하지 못하면 쿠폰을 사용할 수 없다")
    void 최소_주문_금액을_만족하지_못하면_쿠폰을_사용할_수_없다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");

        Point point = new Point(user);
        point.charge(100000);

        Product product = new Product(1L, "키보드", "무선 키보드", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "색상", "블랙", 5000, 10);

        Cart cart = new Cart(user.getId(), option.getId(), 1); // 5000원

        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 10000, 50000, 100,
                now.minusDays(1), now.plusDays(30)); // 최소 주문금액 50000원
        coupon.setId(1L);

        UserCoupon userCoupon = new UserCoupon(user.getId(), coupon.getId(), now.plusDays(30));
        userCoupon.setId(1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(List.of(cart));
        when(productOptionRepository.findById(1L)).thenReturn(Optional.of(option));
        when(userCouponRepository.findById(1L)).thenReturn(Optional.of(userCoupon));
        when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));

        CreateOrderRequest request = new CreateOrderRequest(userCoupon.getId());

        // When & Then
        assertThatThrownBy(() -> createOrderUseCase.execute(user.getId(), request))
            .isInstanceOf(CouponException.class);
    }

    @Test
    @DisplayName("이미 사용한 쿠폰은 주문에 사용할 수 없다")
    void 이미_사용한_쿠폰은_주문에_사용할_수_없다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");

        Point point = new Point(user);
        point.charge(100000);

        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 50000, 10);

        Cart cart = new Cart(user.getId(), option.getId(), 2);

        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 10000, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        coupon.setId(1L);

        UserCoupon userCoupon = new UserCoupon(user.getId(), coupon.getId(), now.plusDays(30));
        userCoupon.use(); // 쿠폰 사용 처리
        userCoupon.setId(1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(List.of(cart));
        when(productOptionRepository.findById(1L)).thenReturn(Optional.of(option));
        when(userCouponRepository.findById(1L)).thenReturn(Optional.of(userCoupon));

        CreateOrderRequest request = new CreateOrderRequest(userCoupon.getId());

        // When & Then
        assertThatThrownBy(() -> createOrderUseCase.execute(user.getId(), request))
            .isInstanceOf(CouponException.class);
    }
}
