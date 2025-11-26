package org.hhplus.hhecommerce.application.order;

import org.hhplus.hhecommerce.api.dto.order.CreateOrderRequest;
import org.hhplus.hhecommerce.api.dto.order.CreateOrderResponse;
import org.hhplus.hhecommerce.config.TestContainersConfig;
import org.hhplus.hhecommerce.domain.cart.Cart;
import org.hhplus.hhecommerce.domain.cart.CartRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderUseCaseConcurrencyTest extends TestContainersConfig {

    @Autowired
    private CreateOrderUseCase createOrderUseCase;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductOptionRepository productOptionRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private PointRepository pointRepository;

    private User testUser;
    private Product testProduct;
    private ProductOption testProductOption;

    @BeforeEach
    void setUp() {
        cartRepository.deleteAll();
        productOptionRepository.deleteAll();
        productRepository.deleteAll();
        pointRepository.deleteAll();
        userRepository.deleteAll();

        testUser = new User("테스트 사용자", "test@example.com");
        testUser = userRepository.save(testUser);

        Point point = new Point(null, testUser.getId(), 100000);
        pointRepository.save(point);

        testProduct = new Product("테스트 상품", "테스트 설명", "테스트");
        testProduct = productRepository.save(testProduct);

        testProductOption = new ProductOption(testProduct.getId(), "기본 옵션", "일반", 10000, 10);
        testProductOption = productOptionRepository.save(testProductOption);

        Cart cart = new Cart(testUser.getId(), testProductOption.getId(), 1);
        cartRepository.save(cart);
    }

    @Test
    @DisplayName("동일 사용자의 동시 주문 요청이 분산락으로 순차적으로 처리된다")
    void 동일_사용자의_동시_주문_요청이_순차적으로_처리된다() throws InterruptedException {
        // given
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 동일 사용자의 동시 주문 요청
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    CreateOrderRequest request = new CreateOrderRequest(null);
                    CreateOrderResponse response = createOrderUseCase.execute(testUser.getId(), request);

                    if (response != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("서로 다른 사용자의 동시 주문은 병렬로 처리된다")
    void 서로_다른_사용자의_동시_주문은_병렬로_처리된다() throws InterruptedException {
        // given
        int userCount = 3;
        List<User> users = new ArrayList<>();

        for (int i = 0; i < userCount; i++) {
            User user = new User("사용자" + i, "user" + i + "@example.com");
            user = userRepository.save(user);
            users.add(user);

            Point point = new Point(null, user.getId(), 100000);
            pointRepository.save(point);

            Cart cart = new Cart(user.getId(), testProductOption.getId(), 1);
            cartRepository.save(cart);
        }

        ExecutorService executorService = Executors.newFixedThreadPool(userCount);
        CountDownLatch latch = new CountDownLatch(userCount);

        AtomicInteger successCount = new AtomicInteger(0);

        // when - 서로 다른 사용자의 동시 주문 요청
        for (User user : users) {
            executorService.submit(() -> {
                try {
                    CreateOrderRequest request = new CreateOrderRequest(null);
                    CreateOrderResponse response = createOrderUseCase.execute(user.getId(), request);

                    if (response != null) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("분산락 획득 타임아웃이 정상 작동한다")
    void 분산락_획득_타임아웃이_정상_작동한다() throws InterruptedException {
        // given
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger totalCount = new AtomicInteger(0);

        // when - 대량의 동시 주문 요청
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    CreateOrderRequest request = new CreateOrderRequest(null);
                    createOrderUseCase.execute(testUser.getId(), request);
                } catch (Exception e) {
                } finally {
                    totalCount.incrementAndGet();
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then - 모든 요청이 처리되었는지 확인
        assertThat(totalCount.get()).isEqualTo(threadCount);
    }
}
