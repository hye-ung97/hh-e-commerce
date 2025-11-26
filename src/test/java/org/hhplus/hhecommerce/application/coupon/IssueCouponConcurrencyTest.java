package org.hhplus.hhecommerce.application.coupon;

import org.hhplus.hhecommerce.api.dto.coupon.IssueCouponResponse;
import org.hhplus.hhecommerce.config.TestContainersConfig;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.CouponType;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.hhplus.hhecommerce.domain.user.User;
import org.hhplus.hhecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IssueCouponConcurrencyTest extends TestContainersConfig {

    @Autowired
    private IssueCouponUseCase issueCouponUseCase;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private UserRepository userRepository;

    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();
        userRepository.deleteAll();

        LocalDateTime now = LocalDateTime.now();
        testCoupon = new Coupon(
                "선착순 100명 쿠폰",
                CouponType.AMOUNT,
                5000,
                null,
                10000,
                100,
                now.minusDays(1),
                now.plusDays(30)
        );
        testCoupon = couponRepository.save(testCoupon);
    }

    @Test
    @DisplayName("Redisson 분산 락으로 선착순 쿠폰 발급이 정확하게 제어된다")
    void 선착순_쿠폰_발급이_정확하게_제어된다() throws InterruptedException {
        // given
        int totalUsers = 200;
        int totalQuantity = 100;

        List<User> users = new ArrayList<>();
        for (int i = 0; i < totalUsers; i++) {
            User user = new User("사용자" + i, "user" + i + "@test.com");
            users.add(userRepository.save(user));
        }

        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // when - 200명이 동시에 100개 쿠폰 발급 시도
        for (User user : users) {
            executorService.submit(() -> {
                try {
                    IssueCouponResponse response = issueCouponUseCase.execute(user.getId(), testCoupon.getId());
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
        assertThat(successCount.get()).isEqualTo(totalQuantity);
        assertThat(failCount.get()).isEqualTo(totalUsers - totalQuantity);
        assertThat(successCount.get() + failCount.get()).isEqualTo(totalUsers);

        long dbIssuedCount = userCouponRepository.count();
        assertThat(dbIssuedCount).isEqualTo(totalQuantity);

        Coupon updatedCoupon = couponRepository.findById(testCoupon.getId()).orElseThrow();
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(totalQuantity);
    }

    @Test
    @DisplayName("동일 사용자가 동시에 여러 번 발급 시도해도 1번만 성공한다")
    void 동일_사용자_중복_발급_방지() throws InterruptedException {
        // given
        User user = new User("테스트 사용자", "test@test.com");
        user = userRepository.save(user);

        int attempts = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(attempts);
        CountDownLatch latch = new CountDownLatch(attempts);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        Long userId = user.getId();

        // when - 동일 사용자가 10번 동시 발급 시도
        for (int i = 0; i < attempts; i++) {
            executorService.submit(() -> {
                try {
                    issueCouponUseCase.execute(userId, testCoupon.getId());
                    successCount.incrementAndGet();
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
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(attempts - 1);

        boolean hasIssued = userCouponRepository.existsByUserIdAndCouponId(userId, testCoupon.getId());
        assertThat(hasIssued).isTrue();

        long userCouponCount = userCouponRepository.findByUserId(userId).size();
        assertThat(userCouponCount).isEqualTo(1);
    }

    @Test
    @DisplayName("쿠폰 수량이 소진되면 추가 발급이 불가능하다")
    void 쿠폰_소진_후_발급_불가() throws InterruptedException {
        // given
        int limitedQuantity = 10;
        Coupon limitedCoupon = new Coupon(
                "한정 10개 쿠폰",
                CouponType.AMOUNT,
                3000,
                null,
                5000,
                limitedQuantity,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );
        limitedCoupon = couponRepository.save(limitedCoupon);

        int totalUsers = 50;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < totalUsers; i++) {
            User user = new User("사용자" + i, "limited" + i + "@test.com");
            users.add(userRepository.save(user));
        }

        ExecutorService executorService = Executors.newFixedThreadPool(30);
        CountDownLatch latch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);

        Long couponId = limitedCoupon.getId();

        // when - 50명이 10개 쿠폰 발급 시도
        for (User user : users) {
            executorService.submit(() -> {
                try {
                    issueCouponUseCase.execute(user.getId(), couponId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then
        assertThat(successCount.get()).isEqualTo(limitedQuantity);

        Coupon updatedCoupon = couponRepository.findById(couponId).orElseThrow();
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(limitedQuantity);
    }

    @Test
    @DisplayName("Redisson 분산 락이 원자적으로 동작한다")
    void 분산_락_원자성_테스트() throws InterruptedException {
        // given
        int concurrentRequests = 1000;
        int maxQuantity = 500;

        Coupon largeCoupon = new Coupon(
                "대용량 쿠폰",
                CouponType.RATE,
                10,
                5000,
                20000,
                maxQuantity,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );
        largeCoupon = couponRepository.save(largeCoupon);

        List<User> users = new ArrayList<>();
        for (int i = 0; i < concurrentRequests; i++) {
            User user = new User("사용자" + i, "large" + i + "@test.com");
            users.add(userRepository.save(user));
        }

        ExecutorService executorService = Executors.newFixedThreadPool(100);
        CountDownLatch latch = new CountDownLatch(concurrentRequests);

        AtomicInteger successCount = new AtomicInteger(0);

        Long couponId = largeCoupon.getId();

        // when - 1000명이 500개 쿠폰 동시 발급
        for (User user : users) {
            executorService.submit(() -> {
                try {
                    issueCouponUseCase.execute(user.getId(), couponId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // 발급 실패
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then - 정확히 500개만 발급되어야 함
        assertThat(successCount.get()).isEqualTo(maxQuantity);

        Coupon updatedCoupon = couponRepository.findById(couponId).orElseThrow();
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(maxQuantity);
    }
}
