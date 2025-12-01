package org.hhplus.hhecommerce.infrastructure.coupon;

import org.hhplus.hhecommerce.config.TestContainersConfig;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueResult;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.CouponType;
import org.hhplus.hhecommerce.domain.coupon.UserCoupon;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = "coupon.issue.strategy=redis")
class RedisCouponIssueManagerTest extends TestContainersConfig {

    @Autowired
    private RedisCouponIssueManager redisCouponIssueManager;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private Coupon testCoupon;

    @BeforeEach
    void setUp() {
        // DB 초기화
        userCouponRepository.deleteAll();
        couponRepository.deleteAll();

        // Redis 초기화
        Set<String> keys = redisTemplate.keys("coupon:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        // 테스트 쿠폰 생성
        LocalDateTime now = LocalDateTime.now();
        testCoupon = new Coupon(
                "선착순 테스트 쿠폰",
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
    @DisplayName("Redis에 재고가 초기화되지 않았을 때 DB에서 자동 동기화된다")
    void DB에서_자동_동기화된다() {
        // Given: Redis에 재고가 없는 상태
        assertThat(redisCouponIssueManager.hasStockKey(testCoupon.getId())).isFalse();

        // When: 쿠폰 발급 시도
        CouponIssueResult result = redisCouponIssueManager.tryIssue(testCoupon.getId(), 1L);

        // Then: DB에서 동기화 후 발급 성공 (PENDING 상태)
        assertThat(result).isEqualTo(CouponIssueResult.SUCCESS);
        assertThat(redisCouponIssueManager.hasStockKey(testCoupon.getId())).isTrue();
        assertThat(redisCouponIssueManager.getRemainingStock(testCoupon.getId())).isEqualTo(99);
    }

    @Test
    @DisplayName("tryIssue 후 confirm 호출 시 issued로 이동한다")
    void tryIssue_후_confirm_호출() {
        // Given
        redisCouponIssueManager.initializeStock(testCoupon.getId(), 100);

        // When
        CouponIssueResult result = redisCouponIssueManager.tryIssue(testCoupon.getId(), 1L);
        assertThat(result).isEqualTo(CouponIssueResult.SUCCESS);

        assertThat(redisCouponIssueManager.hasAlreadyIssued(testCoupon.getId(), 1L)).isFalse();
        assertThat(redisCouponIssueManager.getPendingUsers(testCoupon.getId())).containsKey("1");

        redisCouponIssueManager.confirm(testCoupon.getId(), 1L);

        // Then
        assertThat(redisCouponIssueManager.hasAlreadyIssued(testCoupon.getId(), 1L)).isTrue();
        assertThat(redisCouponIssueManager.getPendingUsers(testCoupon.getId())).doesNotContainKey("1");
        assertThat(redisCouponIssueManager.getIssuedCount(testCoupon.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("tryIssue 후 rollback 호출 시 재고가 복구된다")
    void tryIssue_후_rollback_호출() {
        // Given
        redisCouponIssueManager.initializeStock(testCoupon.getId(), 100);

        // When
        CouponIssueResult result = redisCouponIssueManager.tryIssue(testCoupon.getId(), 1L);
        assertThat(result).isEqualTo(CouponIssueResult.SUCCESS);
        assertThat(redisCouponIssueManager.getRemainingStock(testCoupon.getId())).isEqualTo(99);

        redisCouponIssueManager.rollback(testCoupon.getId(), 1L);

        // Then
        assertThat(redisCouponIssueManager.getRemainingStock(testCoupon.getId())).isEqualTo(100);
        assertThat(redisCouponIssueManager.getPendingUsers(testCoupon.getId())).doesNotContainKey("1");
        assertThat(redisCouponIssueManager.hasAlreadyIssued(testCoupon.getId(), 1L)).isFalse();
    }

    @Test
    @DisplayName("confirm 후 중복 발급 시도 시 ALREADY_ISSUED를 반환한다")
    void 중복_발급_방지() {
        // Given: 재고 초기화 및 첫 발급 + confirm
        redisCouponIssueManager.initializeStock(testCoupon.getId(), 100);
        CouponIssueResult firstResult = redisCouponIssueManager.tryIssue(testCoupon.getId(), 1L);
        assertThat(firstResult).isEqualTo(CouponIssueResult.SUCCESS);
        redisCouponIssueManager.confirm(testCoupon.getId(), 1L);

        // When: 같은 사용자가 다시 발급 시도
        CouponIssueResult secondResult = redisCouponIssueManager.tryIssue(testCoupon.getId(), 1L);

        // Then: 중복 발급 방지
        assertThat(secondResult).isEqualTo(CouponIssueResult.ALREADY_ISSUED);
        assertThat(redisCouponIssueManager.getRemainingStock(testCoupon.getId())).isEqualTo(99);
    }

    @Test
    @DisplayName("PENDING 상태에서 동일 사용자 재요청 시 PENDING_IN_PROGRESS를 반환한다")
    void PENDING_상태_중복_요청() {
        // Given: 발급 시도 (PENDING 상태, confirm 안함)
        redisCouponIssueManager.initializeStock(testCoupon.getId(), 100);
        CouponIssueResult firstResult = redisCouponIssueManager.tryIssue(testCoupon.getId(), 1L);
        assertThat(firstResult).isEqualTo(CouponIssueResult.SUCCESS);

        // When: 같은 사용자가 다시 발급 시도 (5초 내)
        CouponIssueResult secondResult = redisCouponIssueManager.tryIssue(testCoupon.getId(), 1L);

        // Then: PENDING 상태이므로 PENDING_IN_PROGRESS 반환
        assertThat(secondResult).isEqualTo(CouponIssueResult.PENDING_IN_PROGRESS);
        assertThat(redisCouponIssueManager.getRemainingStock(testCoupon.getId())).isEqualTo(99);
    }

    @Test
    @DisplayName("재고 소진 시 OUT_OF_STOCK을 반환한다")
    void 재고_소진_처리() {
        // Given: 재고 1개로 초기화
        redisCouponIssueManager.initializeStock(testCoupon.getId(), 1);

        // 첫 번째 사용자 발급 성공
        CouponIssueResult firstResult = redisCouponIssueManager.tryIssue(testCoupon.getId(), 1L);
        assertThat(firstResult).isEqualTo(CouponIssueResult.SUCCESS);

        // When: 두 번째 사용자 발급 시도
        CouponIssueResult secondResult = redisCouponIssueManager.tryIssue(testCoupon.getId(), 2L);

        // Then: 재고 소진
        assertThat(secondResult).isEqualTo(CouponIssueResult.OUT_OF_STOCK);
    }

    @Test
    @DisplayName("Lua Script로 동시성 요청에서 정확히 재고만큼만 발급된다")
    void 동시성_테스트_정확한_재고_제어() throws InterruptedException {
        // Given
        int totalQuantity = 100;
        int totalUsers = 200;

        redisCouponIssueManager.initializeStock(testCoupon.getId(), totalQuantity);

        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(totalUsers);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger alreadyIssuedCount = new AtomicInteger(0);
        AtomicInteger outOfStockCount = new AtomicInteger(0);

        // When: 200명이 동시에 100개 쿠폰 발급 시도
        for (int i = 1; i <= totalUsers; i++) {
            final long userId = i;
            executorService.submit(() -> {
                try {
                    CouponIssueResult result = redisCouponIssueManager.tryIssue(testCoupon.getId(), userId);
                    switch (result) {
                        case SUCCESS -> {
                            successCount.incrementAndGet();
                            redisCouponIssueManager.confirm(testCoupon.getId(), userId);
                        }
                        case ALREADY_ISSUED -> alreadyIssuedCount.incrementAndGet();
                        case OUT_OF_STOCK -> outOfStockCount.incrementAndGet();
                        default -> {}
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then
        assertThat(successCount.get()).isEqualTo(totalQuantity);
        assertThat(outOfStockCount.get()).isEqualTo(totalUsers - totalQuantity);
        assertThat(alreadyIssuedCount.get()).isEqualTo(0); // 각 사용자가 1번씩만 요청

        assertThat(redisCouponIssueManager.getRemainingStock(testCoupon.getId())).isEqualTo(0);
        assertThat(redisCouponIssueManager.getIssuedCount(testCoupon.getId())).isEqualTo(totalQuantity);
    }

    @Test
    @DisplayName("동일 사용자가 동시에 여러 번 요청해도 1번만 성공한다")
    void 동일_사용자_동시_요청_중복_방지() throws InterruptedException {
        // Given
        redisCouponIssueManager.initializeStock(testCoupon.getId(), 100);

        int attempts = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(attempts);
        CountDownLatch latch = new CountDownLatch(attempts);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger pendingInProgressCount = new AtomicInteger(0);

        Long userId = 1L;

        // When: 동일 사용자가 10번 동시 발급 시도
        for (int i = 0; i < attempts; i++) {
            executorService.submit(() -> {
                try {
                    CouponIssueResult result = redisCouponIssueManager.tryIssue(testCoupon.getId(), userId);
                    if (result == CouponIssueResult.SUCCESS) {
                        successCount.incrementAndGet();
                    } else if (result == CouponIssueResult.PENDING_IN_PROGRESS) {
                        pendingInProgressCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        // Then: 정확히 1번만 성공, 나머지는 PENDING_IN_PROGRESS
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(pendingInProgressCount.get()).isEqualTo(attempts - 1);
        assertThat(redisCouponIssueManager.getRemainingStock(testCoupon.getId())).isEqualTo(99);
    }

    @Test
    @DisplayName("confirm 후 hasAlreadyIssued가 true를 반환한다")
    void hasAlreadyIssued_조회() {
        // Given
        redisCouponIssueManager.initializeStock(testCoupon.getId(), 100);

        // When: 발급 전
        boolean beforeIssue = redisCouponIssueManager.hasAlreadyIssued(testCoupon.getId(), 1L);

        // 발급 수행
        redisCouponIssueManager.tryIssue(testCoupon.getId(), 1L);

        // PENDING 상태 (confirm 전)
        boolean afterIssuePending = redisCouponIssueManager.hasAlreadyIssued(testCoupon.getId(), 1L);

        // confirm 후
        redisCouponIssueManager.confirm(testCoupon.getId(), 1L);
        boolean afterConfirm = redisCouponIssueManager.hasAlreadyIssued(testCoupon.getId(), 1L);

        // Then
        assertThat(beforeIssue).isFalse();
        assertThat(afterIssuePending).isFalse(); // PENDING 상태에서는 아직 issued가 아님
        assertThat(afterConfirm).isTrue(); // confirm 후에는 issued
    }

    @Test
    @DisplayName("DB에 이미 발급된 사용자가 있으면 Redis 동기화 시 함께 동기화된다")
    void 기존_발급자_동기화() {
        // Given: DB에 이미 발급된 사용자 존재
        UserCoupon existingUserCoupon = new UserCoupon(999L, testCoupon.getId(), LocalDateTime.now().plusDays(30));
        userCouponRepository.save(existingUserCoupon);

        // When: 쿠폰 발급 시도 (Redis 동기화 트리거)
        CouponIssueResult result = redisCouponIssueManager.tryIssue(testCoupon.getId(), 1L);

        // Then: 새 사용자는 발급 성공
        assertThat(result).isEqualTo(CouponIssueResult.SUCCESS);

        // 기존 사용자는 Redis에도 발급된 상태로 동기화됨
        assertThat(redisCouponIssueManager.hasAlreadyIssued(testCoupon.getId(), 999L)).isTrue();

        // 기존 사용자 재발급 시도 시 실패
        CouponIssueResult existingUserResult = redisCouponIssueManager.tryIssue(testCoupon.getId(), 999L);
        assertThat(existingUserResult).isEqualTo(CouponIssueResult.ALREADY_ISSUED);
    }
}
