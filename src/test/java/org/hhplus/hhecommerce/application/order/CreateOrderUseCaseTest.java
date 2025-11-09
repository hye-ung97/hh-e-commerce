package org.hhplus.hhecommerce.application.order;

import org.hhplus.hhecommerce.api.dto.order.CreateOrderRequest;
import org.hhplus.hhecommerce.api.dto.order.CreateOrderResponse;
import org.hhplus.hhecommerce.domain.cart.Cart;
import org.hhplus.hhecommerce.domain.cart.CartRepository;
import org.hhplus.hhecommerce.domain.coupon.*;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.hhplus.hhecommerce.domain.order.*;
import org.hhplus.hhecommerce.domain.order.exception.OrderException;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

class CreateOrderUseCaseTest {

    private CreateOrderUseCase createOrderUseCase;
    private TestOrderRepository orderRepository;
    private TestCartRepository cartRepository;
    private TestUserRepository userRepository;
    private TestPointRepository pointRepository;
    private TestUserCouponRepository userCouponRepository;
    private TestProductOptionRepository productOptionRepository;
    private TestCouponRepository couponRepository;

    @BeforeEach
    void setUp() {
        orderRepository = new TestOrderRepository();
        cartRepository = new TestCartRepository();
        userRepository = new TestUserRepository();
        pointRepository = new TestPointRepository();
        userCouponRepository = new TestUserCouponRepository();
        productOptionRepository = new TestProductOptionRepository();
        couponRepository = new TestCouponRepository();

        createOrderUseCase = new CreateOrderUseCase(orderRepository, cartRepository, userRepository,
                pointRepository, userCouponRepository, productOptionRepository, couponRepository);
    }

    @Test
    @DisplayName("정상적으로 주문을 생성할 수 있다")
    void 정상적으로_주문을_생성할_수_있다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");
        userRepository.save(user);

        Point point = new Point(user);
        point.charge(100000);
        pointRepository.save(point);

        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 50000, 10);
        productOptionRepository.save(option);

        Cart cart = new Cart(user.getId(), option.getId(), 2);
        cartRepository.save(cart);

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
        userRepository.save(user);

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
        userRepository.save(user);

        Point point = new Point(user);
        point.charge(100000);
        pointRepository.save(point);

        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 50000, 1);
        productOptionRepository.save(option);

        Cart cart = new Cart(user.getId(), option.getId(), 5); // 재고보다 많은 수량
        cartRepository.save(cart);

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
        userRepository.save(user);

        Point point = new Point(user);
        point.charge(10000); // 부족한 포인트
        pointRepository.save(point);

        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 50000, 10);
        productOptionRepository.save(option);

        Cart cart = new Cart(user.getId(), option.getId(), 2);
        cartRepository.save(cart);

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
        userRepository.save(user);

        Point point = new Point(user);
        point.charge(100000); // 충분한 포인트 충전
        pointRepository.save(point);

        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 20000, 10);
        productOptionRepository.save(option);

        Cart cart = new Cart(user.getId(), option.getId(), 3);
        cartRepository.save(cart);

        CreateOrderRequest request = new CreateOrderRequest(null);

        // When
        createOrderUseCase.execute(user.getId(), request);

        // Then
        ProductOption updatedOption = productOptionRepository.findById(option.getId()).orElseThrow();
        assertThat(updatedOption.getStock()).isEqualTo(7); // 10 - 3
    }

    @Test
    @DisplayName("주문 완료 후 장바구니가 비워진다")
    void 주문_완료_후_장바구니가_비워진다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");
        userRepository.save(user);

        Point point = new Point(user);
        point.charge(100000);
        pointRepository.save(point);

        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 50000, 10);
        productOptionRepository.save(option);

        Cart cart = new Cart(user.getId(), option.getId(), 2);
        cartRepository.save(cart);

        CreateOrderRequest request = new CreateOrderRequest(null);

        // When
        createOrderUseCase.execute(user.getId(), request);

        // Then
        List<Cart> carts = cartRepository.findByUserId(user.getId());
        assertThat(carts).isEmpty();
    }

    @Test
    @DisplayName("쿠폰을 적용하여 주문할 수 있다")
    void 쿠폰을_적용하여_주문할_수_있다() {
        // Given
        User user = new User(1L, "테스트유저", "test@test.com");
        userRepository.save(user);

        Point point = new Point(user);
        point.charge(100000);
        pointRepository.save(point);

        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 50000, 10);
        productOptionRepository.save(option);

        Cart cart = new Cart(user.getId(), option.getId(), 2);
        cartRepository.save(cart);

        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 10000, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        couponRepository.save(coupon);

        UserCoupon userCoupon = new UserCoupon(user.getId(), coupon.getId(), now.plusDays(30));
        userCouponRepository.save(userCoupon);

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
        userRepository.save(user);

        Point point = new Point(user);
        point.charge(100000);
        pointRepository.save(point);

        Product product = new Product(1L, "키보드", "무선 키보드", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "색상", "블랙", 5000, 10);
        productOptionRepository.save(option);

        Cart cart = new Cart(user.getId(), option.getId(), 1); // 5000원
        cartRepository.save(cart);

        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 10000, 50000, 100,
                now.minusDays(1), now.plusDays(30)); // 최소 주문금액 50000원
        couponRepository.save(coupon);

        UserCoupon userCoupon = new UserCoupon(user.getId(), coupon.getId(), now.plusDays(30));
        userCouponRepository.save(userCoupon);

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
        userRepository.save(user);

        Point point = new Point(user);
        point.charge(100000);
        pointRepository.save(point);

        Product product = new Product(1L, "노트북", "고성능 노트북", "전자제품", ProductStatus.ACTIVE);
        ProductOption option = new ProductOption(1L, product, "RAM", "16GB", 50000, 10);
        productOptionRepository.save(option);

        Cart cart = new Cart(user.getId(), option.getId(), 2);
        cartRepository.save(cart);

        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 10000, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        couponRepository.save(coupon);

        UserCoupon userCoupon = new UserCoupon(user.getId(), coupon.getId(), now.plusDays(30));
        userCoupon.use(); // 쿠폰 사용 처리
        userCouponRepository.save(userCoupon);

        CreateOrderRequest request = new CreateOrderRequest(userCoupon.getId());

        // When & Then
        assertThatThrownBy(() -> createOrderUseCase.execute(user.getId(), request))
            .isInstanceOf(CouponException.class);
    }

    // 테스트 전용 Mock Repository
    static class TestOrderRepository implements OrderRepository {
        private final Map<Long, Order> store = new HashMap<>();
        private Long idCounter = 1L;

        @Override
        public Order save(Order order) {
            if (order.getId() == null) {
                order.setId(idCounter++);
            }
            store.put(order.getId(), order);
            return order;
        }

        @Override
        public Optional<Order> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Order> findByUserId(Long userId) {
            return store.values().stream()
                    .filter(o -> o.getUser().getId().equals(userId))
                    .collect(Collectors.toList());
        }

        @Override
        public List<Order> findAll() {
            return new ArrayList<>(store.values());
        }
    }

    static class TestCartRepository implements CartRepository {
        private final Map<Long, Cart> store = new HashMap<>();
        private Long idCounter = 1L;

        @Override
        public Cart save(Cart cart) {
            if (cart.getId() == null) {
                cart.setId(idCounter++);
            }
            store.put(cart.getId(), cart);
            return cart;
        }

        @Override
        public Optional<Cart> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Cart> findByUserId(Long userId) {
            return store.values().stream()
                    .filter(c -> c.getUserId().equals(userId))
                    .collect(Collectors.toList());
        }

        @Override
        public List<Cart> findByUserId(Long userId, int page, int size) {
            return findByUserId(userId);
        }

        @Override
        public int countByUserId(Long userId) {
            return (int) store.values().stream()
                    .filter(c -> c.getUserId().equals(userId))
                    .count();
        }

        @Override
        public Optional<Cart> findByUserIdAndProductOptionId(Long userId, Long productOptionId) {
            return store.values().stream()
                    .filter(c -> c.getUserId().equals(userId) && c.getProductOptionId().equals(productOptionId))
                    .findFirst();
        }

        @Override
        public void delete(Cart cart) {
            store.remove(cart.getId());
        }

        @Override
        public void deleteAllByUserId(Long userId) {
            List<Cart> userCarts = findByUserId(userId);
            userCarts.forEach(this::delete);
        }
    }

    static class TestUserRepository implements UserRepository {
        private final Map<Long, User> store = new HashMap<>();

        @Override
        public User save(User user) {
            store.put(user.getId(), user);
            return user;
        }

        @Override
        public Optional<User> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public Optional<User> findByEmail(String email) {
            return Optional.empty();
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>();
        }

        @Override
        public void delete(User user) {
        }
    }

    static class TestPointRepository implements PointRepository {
        private final Map<Long, Point> store = new HashMap<>();
        private final Map<Long, Point> userPointStore = new HashMap<>();
        private Long idCounter = 1L;

        @Override
        public Point save(Point point) {
            if (point.getId() == null) {
                point.setId(idCounter++);
            }
            store.put(point.getId(), point);
            userPointStore.put(point.getUser().getId(), point);
            return point;
        }

        @Override
        public Optional<Point> findByUserId(Long userId) {
            return Optional.ofNullable(userPointStore.get(userId));
        }

        @Override
        public Optional<Point> findById(Long id) {
            return Optional.empty();
        }

        @Override
        public void delete(Point point) {
        }
    }

    static class TestUserCouponRepository implements UserCouponRepository {
        private final Map<Long, UserCoupon> store = new HashMap<>();
        private Long idCounter = 1L;

        @Override
        public UserCoupon save(UserCoupon userCoupon) {
            if (userCoupon.getId() == null) {
                userCoupon.setId(idCounter++);
            }
            store.put(userCoupon.getId(), userCoupon);
            return userCoupon;
        }

        @Override
        public Optional<UserCoupon> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<UserCoupon> findByUserId(Long userId) {
            return store.values().stream()
                    .filter(uc -> uc.getUserId().equals(userId))
                    .collect(Collectors.toList());
        }

        @Override
        public boolean existsByUserIdAndCouponId(Long userId, Long couponId) {
            return store.values().stream()
                    .anyMatch(uc -> uc.getUserId().equals(userId) && uc.getCouponId().equals(couponId));
        }

        @Override
        public List<UserCoupon> findAvailableByUserId(Long userId, LocalDateTime now) {
            return store.values().stream()
                    .filter(uc -> uc.getUserId().equals(userId))
                    .filter(uc -> uc.getStatus() == CouponStatus.AVAILABLE)
                    .filter(uc -> uc.getExpiredAt().isAfter(now))
                    .collect(Collectors.toList());
        }
    }

    static class TestProductOptionRepository implements ProductOptionRepository {
        private final Map<Long, ProductOption> store = new HashMap<>();

        @Override
        public ProductOption save(ProductOption productOption) {
            store.put(productOption.getId(), productOption);
            return productOption;
        }

        @Override
        public Optional<ProductOption> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<ProductOption> findByProductId(Long productId) {
            return store.values().stream()
                    .filter(po -> po.getProduct().getId().equals(productId))
                    .collect(Collectors.toList());
        }

        @Override
        public List<ProductOption> findAll() {
            return new ArrayList<>();
        }

        @Override
        public void delete(ProductOption productOption) {
        }
    }

    static class TestCouponRepository implements CouponRepository {
        private final Map<Long, Coupon> store = new HashMap<>();
        private Long idCounter = 1L;

        @Override
        public Coupon save(Coupon coupon) {
            if (coupon.getId() == null) {
                coupon.setId(idCounter++);
            }
            store.put(coupon.getId(), coupon);
            return coupon;
        }

        @Override
        public Optional<Coupon> findById(Long id) {
            return Optional.ofNullable(store.get(id));
        }

        @Override
        public List<Coupon> findAvailableCoupons(LocalDateTime now, int page, int size) {
            return store.values().stream()
                    .filter(c -> c.getStartAt().isBefore(now) && c.getEndAt().isAfter(now))
                    .filter(c -> c.getIssuedQuantity() < c.getTotalQuantity())
                    .skip((long) page * size)
                    .limit(size)
                    .collect(Collectors.toList());
        }

        @Override
        public int countAvailableCoupons(LocalDateTime now) {
            return (int) store.values().stream()
                    .filter(c -> c.getStartAt().isBefore(now) && c.getEndAt().isAfter(now))
                    .filter(c -> c.getIssuedQuantity() < c.getTotalQuantity())
                    .count();
        }

        @Override
        public List<Coupon> findAvailableCoupons(LocalDateTime now) {
            return new ArrayList<>();
        }

        @Override
        public List<Coupon> findAll() {
            return new ArrayList<>();
        }

        @Override
        public List<Coupon> findAll(int page, int size) {
            return new ArrayList<>();
        }

        @Override
        public int countAll() {
            return 0;
        }
    }
}
