package org.hhplus.hhecommerce.application.coupon;

import org.hhplus.hhecommerce.api.dto.coupon.IssueCouponResponse;
import org.hhplus.hhecommerce.domain.coupon.*;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class IssueCouponUseCaseTest {

    private IssueCouponUseCase issueCouponUseCase;
    private TestCouponRepository couponRepository;
    private TestUserCouponRepository userCouponRepository;

    @BeforeEach
    void setUp() {
        couponRepository = new TestCouponRepository();
        userCouponRepository = new TestUserCouponRepository();
        issueCouponUseCase = new IssueCouponUseCase(couponRepository, userCouponRepository);
    }

    @Test
    @DisplayName("정상적으로 쿠폰을 발급할 수 있다")
    void 정상적으로_쿠폰을_발급할_수_있다() {
        // Given
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        couponRepository.save(coupon);

        // When
        IssueCouponResponse response = issueCouponUseCase.execute(userId, coupon.getId());

        // Then
        assertAll("IssueCouponResponse 검증",
            () -> assertNotNull(response),
            () -> assertEquals(coupon.getId(), response.couponId()),
            () -> assertEquals("10% 할인", response.couponName()),
            () -> assertTrue(response.message().contains("발급"))
        );
    }

    @Test
    @DisplayName("이미 발급받은 쿠폰은 다시 발급받을 수 없다")
    void 이미_발급받은_쿠폰은_다시_발급받을_수_없다() {
        // Given
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        couponRepository.save(coupon);

        issueCouponUseCase.execute(userId, coupon.getId());

        // When & Then
        assertThrows(CouponException.class, () -> {
            issueCouponUseCase.execute(userId, coupon.getId());
        });
    }

    @Test
    @DisplayName("수량이 소진된 쿠폰은 발급할 수 없다")
    void 수량이_소진된_쿠폰은_발급할_수_없다() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("한정 쿠폰", CouponType.RATE, 10, 5000, 10000, 1,
                now.minusDays(1), now.plusDays(30));
        couponRepository.save(coupon);

        issueCouponUseCase.execute(1L, coupon.getId());

        // When & Then
        assertThrows(Exception.class, () -> {
            issueCouponUseCase.execute(2L, coupon.getId());
        });
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰은 발급할 수 없다")
    void 존재하지_않는_쿠폰은_발급할_수_없다() {
        // When & Then
        assertThrows(CouponException.class, () -> {
            issueCouponUseCase.execute(1L, 999L);
        });
    }

    @Test
    @DisplayName("선착순 쿠폰 발급 시 동시성 제어가 정상 작동한다")
    void 선착순_쿠폰_발급_시_동시성_제어가_정상_작동한다() throws InterruptedException {
        // Given
        LocalDateTime now = LocalDateTime.now();
        int totalQuantity = 10;
        int concurrentUsers = 20;

        Coupon coupon = new Coupon("선착순 쿠폰", CouponType.AMOUNT, 1000, null, 0, totalQuantity,
                now.minusDays(1), now.plusDays(30));
        couponRepository.save(coupon);

        // When - 20명의 사용자가 동시에 쿠폰 발급 시도
        CountDownLatch latch = new CountDownLatch(concurrentUsers);
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentUsers);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 1; i <= concurrentUsers; i++) {
            final long userId = i;
            executorService.submit(() -> {
                try {
                    issueCouponUseCase.execute(userId, coupon.getId());
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

        // Then
        Coupon updatedCoupon = couponRepository.findById(coupon.getId()).orElseThrow();

        assertAll("동시성 제어 검증",
            () -> assertEquals(totalQuantity, successCount.get()), // 정확히 10명만 발급 성공
            () -> assertEquals(concurrentUsers - totalQuantity, failCount.get()), // 나머지 10명은 실패
            () -> assertEquals(totalQuantity, updatedCoupon.getIssuedQuantity()) // 쿠폰의 발급 수량이 정확히 totalQuantity
        );
    }

    @Test
    @DisplayName("동일 사용자가 동시에 같은 쿠폰을 발급받을 수 없다")
    void 동일_사용자가_동시에_같은_쿠폰을_발급받을_수_없다() throws InterruptedException {
        // Given
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();

        Coupon coupon = new Coupon("테스트 쿠폰", CouponType.AMOUNT, 1000, null, 0, 100,
                now.minusDays(1), now.plusDays(30));
        couponRepository.save(coupon);

        // When - 동일 사용자가 5번 동시에 발급 시도
        int attempts = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(attempts);
        ExecutorService executorService = Executors.newFixedThreadPool(attempts);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < attempts; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await(); // 모든 스레드가 동시에 시작하도록
                    issueCouponUseCase.execute(userId, coupon.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // 모든 스레드 동시 시작
        endLatch.await();
        executorService.shutdown();

        // Then
        // 사용자의 쿠폰이 1개만 존재해야 함
        List<UserCoupon> userCoupons = userCouponRepository.findByUserId(userId);
        assertEquals(1, userCoupons.size(), "동일 사용자는 같은 쿠폰을 1번만 발급받을 수 있어야 합니다. 실제: " + userCoupons.size());

        // 성공 횟수 확인 (디버깅용)
        System.out.println("성공: " + successCount.get() + ", 실패: " + failCount.get());
    }

    // 테스트 전용 Mock Repository
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
}
