package org.hhplus.hhecommerce.api.controller;

import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.CouponType;
import org.hhplus.hhecommerce.domain.coupon.UserCoupon;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.hhplus.hhecommerce.domain.user.User;
import org.hhplus.hhecommerce.domain.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("CouponController 통합 테스트")
class CouponControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private Coupon rateCoupon;
    private Coupon amountCoupon;

    @BeforeEach
    void setUp() {
        // 테스트 사용자 생성
        testUser = new User("테스트유저", "test@example.com");
        testUser = userRepository.save(testUser);

        // 할인율 쿠폰 생성 (10% 할인, 최대 5000원)
        rateCoupon = new Coupon(
                "10% 할인 쿠폰",
                CouponType.RATE,
                10,
                5000,
                10000,
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );
        rateCoupon = couponRepository.save(rateCoupon);

        // 고정 금액 쿠폰 생성 (3000원 할인)
        amountCoupon = new Coupon(
                "3000원 할인 쿠폰",
                CouponType.AMOUNT,
                3000,
                null,
                5000,
                50,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );
        amountCoupon = couponRepository.save(amountCoupon);
    }

    @Test
    @DisplayName("발급 가능한 쿠폰 목록 조회 - 성공")
    void getAvailableCoupons_success() throws Exception {
        // when & then
        mockMvc.perform(get("/api/coupons")
                        .param("page", "0")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.coupons[*].name", hasItems("10% 할인 쿠폰", "3000원 할인 쿠폰")))
                .andExpect(jsonPath("$.totalCount", greaterThanOrEqualTo(2)));
    }

    @Test
    @DisplayName("발급 가능한 쿠폰 목록 조회 - 페이징")
    void getAvailableCoupons_withPagination() throws Exception {
        // given - 추가 쿠폰 생성
        for (int i = 3; i <= 10; i++) {
            Coupon coupon = new Coupon(
                    "쿠폰" + i,
                    CouponType.AMOUNT,
                    1000 * i,
                    null,
                    5000,
                    10,
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now().plusDays(30)
            );
            couponRepository.save(coupon);
        }

        // when & then - 첫 페이지
        mockMvc.perform(get("/api/coupons")
                        .param("page", "0")
                        .param("size", "5"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons", hasSize(5)));
    }

    @Test
    @DisplayName("쿠폰 발급 - 성공")
    void issueCoupon_success() throws Exception {
        // when & then
        mockMvc.perform(post("/api/coupons/{couponId}/issue", rateCoupon.getId())
                        .param("userId", testUser.getId().toString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couponId", is(rateCoupon.getId().intValue())))
                .andExpect(jsonPath("$.couponName", is("10% 할인 쿠폰")))
                .andExpect(jsonPath("$.message", notNullValue()));

        // 발급 확인
        boolean exists = userCouponRepository.existsByUserIdAndCouponId(testUser.getId(), rateCoupon.getId());
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("쿠폰 발급 - 중복 발급 (실패)")
    void issueCoupon_duplicateIssue() throws Exception {
        // given - 먼저 쿠폰 발급
        mockMvc.perform(post("/api/coupons/{couponId}/issue", rateCoupon.getId())
                        .param("userId", testUser.getId().toString()))
                .andExpect(status().isOk());

        // when & then - 같은 쿠폰 재발급 시도
        mockMvc.perform(post("/api/coupons/{couponId}/issue", rateCoupon.getId())
                        .param("userId", testUser.getId().toString()))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("쿠폰 발급 - 존재하지 않는 쿠폰 (실패)")
    void issueCoupon_couponNotFound() throws Exception {
        // given
        Long nonExistentCouponId = 99999L;

        // when & then
        mockMvc.perform(post("/api/coupons/{couponId}/issue", nonExistentCouponId)
                        .param("userId", testUser.getId().toString()))
                .andDo(print())
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("보유 쿠폰 조회 - 성공")
    void getUserCoupons_success() throws Exception {
        // given - 쿠폰 발급
        UserCoupon userCoupon = new UserCoupon(testUser.getId(), rateCoupon.getId(), LocalDateTime.now().plusDays(30));
        userCouponRepository.save(userCoupon);

        // when & then
        mockMvc.perform(get("/api/coupons/users/coupons")
                        .param("userId", testUser.getId().toString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons", hasSize(1)))
                .andExpect(jsonPath("$.coupons[0].couponName", is("10% 할인 쿠폰")))
                .andExpect(jsonPath("$.coupons[0].userId", is(testUser.getId().intValue())))
                .andExpect(jsonPath("$.totalCount", is(1)));
    }

    @Test
    @DisplayName("보유 쿠폰 조회 - 쿠폰이 없는 경우")
    void getUserCoupons_noCoupons() throws Exception {
        // when & then
        mockMvc.perform(get("/api/coupons/users/coupons")
                        .param("userId", testUser.getId().toString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons", hasSize(0)))
                .andExpect(jsonPath("$.totalCount", is(0)));
    }

    @Test
    @DisplayName("보유 쿠폰 조회 - 여러 쿠폰")
    void getUserCoupons_multipleCoupons() throws Exception {
        // given - 여러 쿠폰 발급
        UserCoupon userCoupon1 = new UserCoupon(testUser.getId(), rateCoupon.getId(), LocalDateTime.now().plusDays(30));
        UserCoupon userCoupon2 = new UserCoupon(testUser.getId(), amountCoupon.getId(), LocalDateTime.now().plusDays(30));
        userCouponRepository.save(userCoupon1);
        userCouponRepository.save(userCoupon2);

        // when & then
        mockMvc.perform(get("/api/coupons/users/coupons")
                        .param("userId", testUser.getId().toString()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons", hasSize(2)))
                .andExpect(jsonPath("$.totalCount", is(2)));
    }

    @Test
    @DisplayName("사용 가능한 쿠폰 조회 - 성공")
    void getAvailableUserCoupons_success() throws Exception {
        // given - 쿠폰 발급
        UserCoupon userCoupon = new UserCoupon(testUser.getId(), rateCoupon.getId(), LocalDateTime.now().plusDays(30));
        userCouponRepository.save(userCoupon);

        // when & then - 주문 금액 50000원으로 조회
        mockMvc.perform(get("/api/coupons/users/coupons/available")
                        .param("userId", testUser.getId().toString())
                        .param("orderAmount", "50000"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons", hasSize(1)))
                .andExpect(jsonPath("$.orderAmount", is(50000)))
                .andExpect(jsonPath("$.coupons[0].expectedDiscount", is(5000))) // 10%할인, 최대 5000원
                .andExpect(jsonPath("$.coupons[0].finalAmount", is(45000))); // 50000 - 5000
    }

    @Test
    @DisplayName("사용 가능한 쿠폰 조회 - 최소 주문 금액 미만")
    void getAvailableUserCoupons_belowMinOrderAmount() throws Exception {
        // given - 쿠폰 발급 (최소 주문 금액 10000원)
        UserCoupon userCoupon = new UserCoupon(testUser.getId(), rateCoupon.getId(), LocalDateTime.now().plusDays(30));
        userCouponRepository.save(userCoupon);

        // when & then - 주문 금액 5000원 (최소 금액 미만)
        mockMvc.perform(get("/api/coupons/users/coupons/available")
                        .param("userId", testUser.getId().toString())
                        .param("orderAmount", "5000"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons", hasSize(0))) // 사용 불가
                .andExpect(jsonPath("$.totalCount", is(0)));
    }

    @Test
    @DisplayName("사용 가능한 쿠폰 조회 - 고정 금액 쿠폰")
    void getAvailableUserCoupons_amountCoupon() throws Exception {
        // given - 고정 금액 쿠폰 발급 (3000원 할인)
        UserCoupon userCoupon = new UserCoupon(testUser.getId(), amountCoupon.getId(), LocalDateTime.now().plusDays(30));
        userCouponRepository.save(userCoupon);

        // when & then - 주문 금액 10000원
        mockMvc.perform(get("/api/coupons/users/coupons/available")
                        .param("userId", testUser.getId().toString())
                        .param("orderAmount", "10000"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons", hasSize(1)))
                .andExpect(jsonPath("$.coupons[0].expectedDiscount", is(3000))) // 고정 금액
                .andExpect(jsonPath("$.coupons[0].finalAmount", is(7000))); // 10000 - 3000
    }

    @Test
    @DisplayName("쿠폰 발급 - 동시성 테스트: 수량 제한")
    void issueCoupon_concurrency_quantityLimit() throws Exception {
        // given - 수량이 제한된 쿠폰 생성 (총 10개)
        Coupon limitedCoupon = new Coupon(
                "한정 쿠폰",
                CouponType.AMOUNT,
                5000,
                null,
                0,
                10, // 총 10개만 발급 가능
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );
        limitedCoupon = couponRepository.save(limitedCoupon);

        // 20명의 사용자 생성
        int numberOfUsers = 20;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < numberOfUsers; i++) {
            User user = new User("사용자" + i, "user" + i + "@example.com");
            users.add(userRepository.save(user));
        }

        // when - 20명이 동시에 쿠폰 발급 요청
        ExecutorService executorService = Executors.newFixedThreadPool(20);
        CountDownLatch latch = new CountDownLatch(numberOfUsers);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        Long finalCouponId = limitedCoupon.getId();
        for (User user : users) {
            executorService.submit(() -> {
                try {
                    mockMvc.perform(post("/api/coupons/{couponId}/issue", finalCouponId)
                                    .param("userId", user.getId().toString()))
                            .andDo(result -> {
                                if (result.getResponse().getStatus() == 200) {
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

        // then - 정확히 10개만 발급되어야 함
        Coupon updatedCoupon = couponRepository.findById(finalCouponId).orElseThrow();
        assertThat(updatedCoupon.getIssuedQuantity()).isEqualTo(10);
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(10);

        // 실제 발급된 사용자 쿠폰 수 확인
        List<UserCoupon> issuedCoupons = users.stream()
                .flatMap(user -> userCouponRepository.findByUserId(user.getId()).stream())
                .filter(uc -> uc.getCouponId().equals(finalCouponId))
                .toList();
        assertThat(issuedCoupons).hasSize(10);
    }

    @Test
    @DisplayName("쿠폰 발급 - 동시성 테스트: 동일 사용자 중복 발급 방지")
    void issueCoupon_concurrency_preventDuplicateIssue() throws Exception {
        // given - 쿠폰 생성
        Coupon testCoupon = new Coupon(
                "테스트 쿠폰",
                CouponType.AMOUNT,
                1000,
                null,
                0,
                100,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().plusDays(30)
        );
        testCoupon = couponRepository.save(testCoupon);

        // when - 동일 사용자가 10번 동시에 발급 요청
        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);

        Long finalCouponId = testCoupon.getId();
        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    mockMvc.perform(post("/api/coupons/{couponId}/issue", finalCouponId)
                                    .param("userId", testUser.getId().toString()))
                            .andDo(result -> {
                                if (result.getResponse().getStatus() == 200) {
                                    successCount.incrementAndGet();
                                }
                            });
                } catch (Exception e) {
                    // 예외 무시
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // then - 정확히 1개만 발급되어야 함
        assertThat(successCount.get()).isEqualTo(1);

        // 실제 발급된 쿠폰 수 확인
        List<UserCoupon> userCoupons = userCouponRepository.findByUserId(testUser.getId());
        long count = userCoupons.stream()
                .filter(uc -> uc.getCouponId().equals(finalCouponId))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("발급 가능한 쿠폰 목록 조회 - 남은 수량 확인")
    void getAvailableCoupons_checkRemainingQuantity() throws Exception {
        // given - 일부 쿠폰 발급
        rateCoupon.issue();
        rateCoupon.issue();
        couponRepository.save(rateCoupon);

        // when & then
        mockMvc.perform(get("/api/coupons")
                        .param("page", "0")
                        .param("size", "20"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coupons[*].remainingQuantity", everyItem(greaterThanOrEqualTo(0))));
    }
}
