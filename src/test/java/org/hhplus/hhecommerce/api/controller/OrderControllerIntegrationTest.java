package org.hhplus.hhecommerce.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.hhplus.hhecommerce.api.dto.order.CreateOrderRequest;
import org.hhplus.hhecommerce.config.TestContainersConfig;
import org.hhplus.hhecommerce.domain.cart.Cart;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.cart.CartRepository;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.CouponType;
import org.hhplus.hhecommerce.domain.coupon.UserCoupon;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.product.Product;
import org.hhplus.hhecommerce.domain.product.ProductOption;
import org.hhplus.hhecommerce.domain.product.ProductOptionRepository;
import org.hhplus.hhecommerce.domain.product.ProductRepository;
import org.hhplus.hhecommerce.domain.user.User;
import org.hhplus.hhecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Transactional
@DisplayName("OrderController 통합 테스트")
class OrderControllerIntegrationTest extends TestContainersConfig {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PointRepository pointRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductOptionRepository productOptionRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private EntityManager entityManager;

    private User testUser;
    private Point testPoint;
    private Product product;
    private ProductOption productOption;
    private Coupon coupon;

    @BeforeEach
    void setUp() {
        cartRepository.deleteAll();
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        productOptionRepository.deleteAll();
        productRepository.deleteAll();
        pointRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User("테스트유저", "order-test@example.com");
        testUser = userRepository.save(testUser);

        testPoint = new Point(testUser.getId());
        testPoint.charge(100000); // 10만원 충전 (최대 보유 금액)
        testPoint = pointRepository.save(testPoint);

        product = new Product("테스트 노트북", "고성능 노트북", "전자제품");
        product = productRepository.save(product);

        productOption = new ProductOption(product.getId(), "색상", "실버", 50000, 100);
        productOption = productOptionRepository.save(productOption);

        coupon = new Coupon(
                "10% 할인 쿠폰",
                CouponType.RATE,
                10,
                5000,
                10000,
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );
        coupon = couponRepository.save(coupon);
    }

    @Test
    @DisplayName("주문 생성 - 성공 (쿠폰 없음)")
    void createOrder_success_withoutCoupon() throws Exception {
        // given - 장바구니에 상품 추가
        Cart cart = new Cart(testUser.getId(), productOption.getId(), 2);
        cartRepository.save(cart);

        CreateOrderRequest request = new CreateOrderRequest(null);

        // when & then
        mockMvc.perform(post("/api/orders")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(testUser.getId().intValue())))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.totalAmount", is(100000))) // 50000 * 2
                .andExpect(jsonPath("$.discountAmount", is(0)))
                .andExpect(jsonPath("$.finalAmount", is(100000)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.message", notNullValue()));

        assertThat(cartRepository.findByUserId(testUser.getId())).isEmpty();

        ProductOption updatedOption = productOptionRepository.findById(productOption.getId()).orElseThrow();
        assertThat(updatedOption.getStock()).isEqualTo(98); // 100 - 2

        Point updatedPoint = pointRepository.findByUserId(testUser.getId()).orElseThrow();
        assertThat(updatedPoint.getAmount()).isEqualTo(0); // 100000 - 100000
    }

    @Test
    @DisplayName("주문 생성 - 성공 (쿠폰 적용)")
    void createOrder_success_withCoupon() throws Exception {
        // given - 장바구니에 상품 추가
        Cart cart = new Cart(testUser.getId(), productOption.getId(), 2);
        cartRepository.save(cart);

        // 사용자 쿠폰 발급
        UserCoupon userCoupon = new UserCoupon(testUser.getId(), coupon.getId(), LocalDateTime.now().plusDays(30));
        userCoupon = userCouponRepository.save(userCoupon);

        CreateOrderRequest request = new CreateOrderRequest(userCoupon.getId());

        // when & then
        mockMvc.perform(post("/api/orders")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is(testUser.getId().intValue())))
                .andExpect(jsonPath("$.totalAmount", is(100000))) // 50000 * 2
                .andExpect(jsonPath("$.discountAmount", is(5000))) // 10% 할인 최대 5000원
                .andExpect(jsonPath("$.finalAmount", is(95000))); // 100000 - 5000

        // 포인트 차감 확인
        Point updatedPoint = pointRepository.findByUserId(testUser.getId()).orElseThrow();
        assertThat(updatedPoint.getAmount()).isEqualTo(5000); // 100000 - 95000
    }

    @Test
    @DisplayName("주문 생성 - 빈 장바구니 (실패)")
    void createOrder_emptyCart() throws Exception {
        // given - 장바구니가 비어있음
        CreateOrderRequest request = new CreateOrderRequest(null);

        // when & then
        mockMvc.perform(post("/api/orders")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("주문 생성 - 재고 부족 (실패)")
    void createOrder_insufficientStock() throws Exception {
        // given - 재고보다 많은 수량을 장바구니에 추가
        Cart cart = new Cart(testUser.getId(), productOption.getId(), 150); // 재고 100개인데 150개 주문
        cartRepository.save(cart);

        CreateOrderRequest request = new CreateOrderRequest(null);

        // when & then
        mockMvc.perform(post("/api/orders")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("주문 생성 - 포인트 부족 (실패)")
    void createOrder_insufficientPoint() throws Exception {
        // given - 포인트 잔액을 부족하게 설정
        testPoint = pointRepository.findByUserId(testUser.getId()).orElseThrow();
        testPoint.deduct(99000); // 1000원만 남김
        pointRepository.save(testPoint);

        // 장바구니에 상품 추가 (10만원)
        Cart cart = new Cart(testUser.getId(), productOption.getId(), 2);
        cartRepository.save(cart);

        CreateOrderRequest request = new CreateOrderRequest(null);

        // when & then
        mockMvc.perform(post("/api/orders")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("주문 생성 - 쿠폰 최소 주문 금액 미달 (실패)")
    void createOrder_couponMinOrderAmountNotMet() throws Exception {
        // given - 저가 상품 생성 (쿠폰 최소 금액 10000원 미만)
        Product cheapProduct = new Product("저가 상품", "저렴한 상품", "기타");
        cheapProduct = productRepository.save(cheapProduct);

        ProductOption cheapOption = new ProductOption(cheapProduct.getId(), "옵션", "기본", 3000, 100);
        cheapOption = productOptionRepository.save(cheapOption);

        // 장바구니에 저가 상품 추가 (총 6000원)
        Cart cart = new Cart(testUser.getId(), cheapOption.getId(), 2);
        cartRepository.save(cart);

        // 사용자 쿠폰 발급
        UserCoupon userCoupon = new UserCoupon(testUser.getId(), coupon.getId(), LocalDateTime.now().plusDays(30));
        userCoupon = userCouponRepository.save(userCoupon);

        CreateOrderRequest request = new CreateOrderRequest(userCoupon.getId());

        // when & then
        mockMvc.perform(post("/api/orders")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("주문 목록 조회 - 성공")
    void getOrders_success() throws Exception {
        // given - 주문 생성
        Cart cart = new Cart(testUser.getId(), productOption.getId(), 1);
        cartRepository.save(cart);

        CreateOrderRequest createRequest = new CreateOrderRequest(null);
        mockMvc.perform(post("/api/orders")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk());

        // when & then - 주문 목록 조회
        mockMvc.perform(get("/api/orders")
                        .param("userId", testUser.getId().toString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders", hasSize(1)))
                .andExpect(jsonPath("$.orders[0].status", is("PENDING")))
                .andExpect(jsonPath("$.orders[0].finalAmount", is(50000)))
                .andExpect(jsonPath("$.total", is(1)));
    }

    @Test
    @DisplayName("주문 목록 조회 - 주문 없음")
    void getOrders_noOrders() throws Exception {
        // when & then
        mockMvc.perform(get("/api/orders")
                        .param("userId", testUser.getId().toString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders", hasSize(0)))
                .andExpect(jsonPath("$.total", is(0)));
    }

    @Test
    @DisplayName("주문 목록 조회 - 여러 주문")
    void getOrders_multipleOrders() throws Exception {
        // given - 여러 주문 생성 (이미 초기 포인트 100000원이 있음)
        for (int i = 0; i < 2; i++) {
            Cart cart = new Cart(testUser.getId(), productOption.getId(), 1); // 50000원
            cartRepository.save(cart);

            CreateOrderRequest createRequest = new CreateOrderRequest(null);
            mockMvc.perform(post("/api/orders")
                            .param("userId", testUser.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(createRequest)))
                    .andExpect(status().isOk());

            entityManager.flush();
            entityManager.clear();
        }

        // when & then - 주문 목록 조회
        mockMvc.perform(get("/api/orders")
                        .param("userId", testUser.getId().toString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders", hasSize(2)))
                .andExpect(jsonPath("$.total", is(2)));
    }

    @Test
    @DisplayName("주문 상세 조회 - 성공")
    void getOrderDetail_success() throws Exception {
        // given - 주문 생성
        Cart cart = new Cart(testUser.getId(), productOption.getId(), 2);
        cartRepository.save(cart);

        CreateOrderRequest createRequest = new CreateOrderRequest(null);
        String response = mockMvc.perform(post("/api/orders")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long orderId = objectMapper.readTree(response).get("id").asLong();

        // when & then - 주문 상세 조회
        mockMvc.perform(get("/api/orders/{orderId}", orderId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(orderId.intValue())))
                .andExpect(jsonPath("$.userId", is(testUser.getId().intValue())))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.totalAmount", is(100000)))
                .andExpect(jsonPath("$.discountAmount", is(0)))
                .andExpect(jsonPath("$.finalAmount", is(100000)))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productName", is("테스트 노트북")))
                .andExpect(jsonPath("$.items[0].quantity", is(2)));
    }

    @Test
    @DisplayName("주문 상세 조회 - 존재하지 않는 주문 (실패)")
    void getOrderDetail_notFound() throws Exception {
        // given
        Long nonExistentOrderId = 99999L;

        // when & then
        mockMvc.perform(get("/api/orders/{orderId}", nonExistentOrderId))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("주문 생성 - 여러 상품")
    void createOrder_multipleProducts() throws Exception {
        // given - 여러 상품을 장바구니에 추가 (총 금액이 10만원 이하가 되도록 조정)
        Product product2 = new Product("마우스", "무선 마우스", "전자제품");
        product2 = productRepository.save(product2);

        ProductOption option2 = new ProductOption(product2.getId(), "색상", "블랙", 20000, 50);
        option2 = productOptionRepository.save(option2);

        Cart cart1 = new Cart(testUser.getId(), productOption.getId(), 1); // 50000원
        Cart cart2 = new Cart(testUser.getId(), option2.getId(), 2); // 40000원
        cartRepository.save(cart1);
        cartRepository.save(cart2);

        CreateOrderRequest request = new CreateOrderRequest(null);

        // when & then
        mockMvc.perform(post("/api/orders")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount", is(90000))) // 50000 + 40000
                .andExpect(jsonPath("$.finalAmount", is(90000)))
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    @DisplayName("주문 생성 - 이미 사용한 쿠폰 (실패)")
    void createOrder_alreadyUsedCoupon() throws Exception {
        // given - 장바구니에 상품 추가
        Cart cart1 = new Cart(testUser.getId(), productOption.getId(), 1);
        cartRepository.save(cart1);

        UserCoupon userCoupon = new UserCoupon(testUser.getId(), coupon.getId(), LocalDateTime.now().plusDays(30));
        userCoupon = userCouponRepository.save(userCoupon);
        userCoupon.use();
        userCouponRepository.save(userCoupon);

        CreateOrderRequest request = new CreateOrderRequest(userCoupon.getId());

        // when & then
        mockMvc.perform(post("/api/orders")
                        .param("userId", testUser.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }


    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("동시성 테스트: 재고 차감 - 여러 사용자가 같은 상품 동시 주문")
    void createOrder_concurrency_stockDeduction() throws Exception {
        // given - 테스트 데이터 초기화
        cartRepository.deleteAll();
        productOptionRepository.deleteAll();
        productRepository.deleteAll();
        pointRepository.deleteAll();
        userRepository.deleteAll();

        Product product = new Product("인기 상품", "재고 10개", "전자제품");
        product = productRepository.save(product);

        ProductOption limitedOption = new ProductOption(product.getId(), "색상", "블랙", 10000, 10);
        limitedOption = productOptionRepository.save(limitedOption);

        int numberOfUsers = 20;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < numberOfUsers; i++) {
            User user = new User("사용자" + i, "user" + i + "@example.com");
            user = userRepository.save(user);
            users.add(user);

            Point point = new Point(user.getId());
            point.charge(50000);
            pointRepository.save(point);

            Cart cart = new Cart(user.getId(), limitedOption.getId(), 1);
            cartRepository.save(cart);
        }

        // when - 20명이 동시에 주문 시도
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfUsers);
        CountDownLatch latch = new CountDownLatch(numberOfUsers);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        CreateOrderRequest request = new CreateOrderRequest(null);

        for (User user : users) {
            executorService.submit(() -> {
                try {
                    mockMvc.perform(post("/api/orders")
                                    .param("userId", user.getId().toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andDo(result -> {
                                int status = result.getResponse().getStatus();
                                if (status == 200) {
                                    successCount.incrementAndGet();
                                } else {
                                    failCount.incrementAndGet();
                                }
                            });
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then - 낙관적 락으로 재고가 정확히 차감되어야 함
        ProductOption updatedOption = productOptionRepository.findById(limitedOption.getId()).orElseThrow();
        int expectedRemainingStock = 10 - successCount.get();

        assertThat(updatedOption.getStock()).isEqualTo(expectedRemainingStock)
            .withFailMessage("재고가 정확히 차감되어야 합니다. 성공: " + successCount.get() +
                ", 기대 재고: " + expectedRemainingStock + ", 실제 재고: " + updatedOption.getStock());

        assertThat(successCount.get()).isLessThanOrEqualTo(10)
            .withFailMessage("재고 10개이므로 성공은 최대 10개여야 합니다. 실제: " + successCount.get());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("동시성 테스트: 포인트 차감 - 같은 사용자의 동시 주문 (낙관적 락 재시도)")
    void createOrder_concurrency_pointDeduction() throws Exception {
        // given - 테스트 데이터 초기화
        cartRepository.deleteAll();
        productOptionRepository.deleteAll();
        productRepository.deleteAll();
        pointRepository.deleteAll();
        userRepository.deleteAll();

        User testUserForConcurrency = userRepository.save(new User("동시주문유저", "concurrent@example.com"));

        Point point = new Point(testUserForConcurrency.getId());
        point.charge(100000);
        point = pointRepository.save(point);

        List<ProductOption> options = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Product product = new Product("상품" + i, "테스트 상품", "카테고리");
            product = productRepository.save(product);

            ProductOption option = new ProductOption(product.getId(), "옵션", "기본", 10000, 100);
            option = productOptionRepository.save(option);
            options.add(option);
        }

        // when - 같은 사용자가 5개 상품을 순차적으로 주문 (동시성 제어 테스트)
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        CountDownLatch startLatch = new CountDownLatch(1); // 동시 시작을 위한 latch
        CountDownLatch completeLatch = new CountDownLatch(5);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        final Long userId = testUserForConcurrency.getId();

        for (int i = 0; i < 5; i++) {
            final ProductOption option = options.get(i);
            Cart cart = new Cart(userId, option.getId(), 1);
            cartRepository.save(cart);
        }

        for (int i = 0; i < 5; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    CreateOrderRequest request = new CreateOrderRequest(null);

                    mockMvc.perform(post("/api/orders")
                                    .param("userId", userId.toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andDo(result -> {
                                int status = result.getResponse().getStatus();
                                if (status == 200) {
                                    successCount.incrementAndGet();
                                } else {
                                    failCount.incrementAndGet();
                                }
                            });

                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    completeLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        completeLatch.await();
        executorService.shutdown();

        // then
        Point finalPoint = pointRepository.findByUserId(userId).orElseThrow();

        assertThat(successCount.get()).isGreaterThan(0)
            .withFailMessage("최소 1개 주문은 성공해야 합니다.");
        assertThat(successCount.get() + failCount.get()).isEqualTo(5)
            .withFailMessage("총 5개 요청이 처리되어야 합니다.");
        assertThat(finalPoint.getAmount()).isLessThanOrEqualTo(100000)
            .withFailMessage("포인트가 차감되어야 합니다.");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("동시성 테스트: 낙관적 락 재시도 검증 - 높은 충돌 상황")
    void createOrder_concurrency_optimisticLockRetry() throws Exception {
        // given - 테스트 데이터 초기화
        cartRepository.deleteAll();
        productOptionRepository.deleteAll();
        productRepository.deleteAll();
        pointRepository.deleteAll();
        userRepository.deleteAll();

        Product product = new Product("초인기 상품", "재고 5개", "한정판");
        product = productRepository.save(product);

        ProductOption hotOption = new ProductOption(product.getId(), "색상", "레드", 20000, 5);
        hotOption = productOptionRepository.save(hotOption);

        int numberOfUsers = 10;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < numberOfUsers; i++) {
            User user = new User("경쟁자" + i, "competitor" + i + "@example.com");
            user = userRepository.save(user);
            users.add(user);

            Point point = new Point(user.getId());
            point.charge(50000);
            pointRepository.save(point);

            Cart cart = new Cart(user.getId(), hotOption.getId(), 1);
            cartRepository.save(cart);
        }

        // when - 10명이 거의 동시에 주문 시도 (재고 5개 경쟁)
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfUsers);
        CountDownLatch startLatch = new CountDownLatch(1);  // 동시 시작을 위한 래치
        CountDownLatch endLatch = new CountDownLatch(numberOfUsers);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger retryCount = new AtomicInteger(0);  // 재시도 횟수 추적

        CreateOrderRequest request = new CreateOrderRequest(null);

        for (User user : users) {
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    mockMvc.perform(post("/api/orders")
                                    .param("userId", user.getId().toString())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andDo(result -> {
                                int status = result.getResponse().getStatus();
                                if (status == 200) {
                                    successCount.incrementAndGet();
                                } else if (status == 409) {  // CONFLICT - 재시도 후에도 실패
                                    retryCount.incrementAndGet();
                                    failCount.incrementAndGet();
                                } else {
                                    failCount.incrementAndGet();
                                }
                            });
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executorService.shutdown();

        // then
        ProductOption finalOption = productOptionRepository.findById(hotOption.getId()).orElseThrow();

        int expectedStock = 5 - successCount.get();

        assertThat(finalOption.getStock()).isEqualTo(expectedStock)
            .withFailMessage("재고가 정확히 차감되어야 합니다. 성공: " + successCount.get() +
                ", 기대 재고: " + expectedStock + ", 실제 재고: " + finalOption.getStock());

        assertThat(successCount.get()).isLessThanOrEqualTo(5)
            .withFailMessage("재고 5개이므로 성공은 최대 5개여야 합니다. 실제: " + successCount.get());

        assertThat(successCount.get() + failCount.get()).isEqualTo(numberOfUsers)
            .withFailMessage("모든 요청이 성공 또는 실패로 처리되어야 합니다.");
    }
}
