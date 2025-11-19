package org.hhplus.hhecommerce.application.coupon;

import org.hhplus.hhecommerce.api.dto.coupon.IssueCouponResponse;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponType;
import org.hhplus.hhecommerce.domain.coupon.UserCoupon;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueCouponUseCaseTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @InjectMocks
    private IssueCouponUseCase issueCouponUseCase;

    @Test
    @DisplayName("정상적으로 쿠폰을 발급할 수 있다")
    void 정상적으로_쿠폰을_발급할_수_있다() {
        // Given
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        coupon.setId(1L);

        when(couponRepository.findByIdWithLock(1L)).thenReturn(Optional.of(coupon));
        when(userCouponRepository.existsByUserIdAndCouponId(userId, 1L)).thenReturn(false);
        when(userCouponRepository.save(any(UserCoupon.class))).thenAnswer(invocation -> {
            UserCoupon userCoupon = invocation.getArgument(0);
            userCoupon.setId(1L);
            return userCoupon;
        });

        // When
        IssueCouponResponse response = issueCouponUseCase.execute(userId, coupon.getId());

        // Then
        assertThat(response).isNotNull();
        assertThat(response.couponId()).isEqualTo(coupon.getId());
        assertThat(response.couponName()).isEqualTo("10% 할인");
        assertThat(response.message()).contains("발급");
    }

    @Test
    @DisplayName("이미 발급받은 쿠폰은 다시 발급받을 수 없다")
    void 이미_발급받은_쿠폰은_다시_발급받을_수_없다() {
        // Given
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        coupon.setId(1L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, 1L)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, coupon.getId()))
            .isInstanceOf(CouponException.class);
    }

    @Test
    @DisplayName("수량이 소진된 쿠폰은 발급할 수 없다")
    void 수량이_소진된_쿠폰은_발급할_수_없다() {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("한정 쿠폰", CouponType.RATE, 10, 5000, 10000, 1,
                now.minusDays(1), now.plusDays(30));
        coupon.setId(1L);
        coupon.issue();

        when(couponRepository.findByIdWithLock(1L)).thenReturn(Optional.of(coupon));

        // When & Then
        assertThatThrownBy(() -> issueCouponUseCase.execute(2L, coupon.getId()))
            .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰은 발급할 수 없다")
    void 존재하지_않는_쿠폰은_발급할_수_없다() {
        // Given
        when(couponRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> issueCouponUseCase.execute(1L, 999L))
            .isInstanceOf(CouponException.class);
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
        coupon.setId(1L);

        when(couponRepository.findByIdWithLock(1L)).thenReturn(Optional.of(coupon));
        when(userCouponRepository.existsByUserIdAndCouponId(anyLong(), eq(1L))).thenReturn(false);
        when(userCouponRepository.save(any(UserCoupon.class))).thenAnswer(invocation -> {
            UserCoupon userCoupon = invocation.getArgument(0);
            userCoupon.setId(System.nanoTime());
            return userCoupon;
        });

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
        assertThat(successCount.get()).isLessThanOrEqualTo(totalQuantity)
            .withFailMessage("성공 횟수는 총 수량을 초과할 수 없습니다. 성공: " + successCount.get() + ", 총 수량: " + totalQuantity);
        assertThat(successCount.get() + failCount.get()).isEqualTo(concurrentUsers)
            .withFailMessage("모든 요청이 처리되어야 합니다");
        assertThat(coupon.getIssuedQuantity()).isLessThanOrEqualTo(totalQuantity)
            .withFailMessage("발급된 수량은 총 수량을 초과할 수 없습니다");
    }

    @Test
    @DisplayName("동일 사용자가 동시에 같은 쿠폰을 발급받을 수 없다")
    void 동일_사용자가_동시에_같은_쿠폰을_발급받을_수_없다() throws InterruptedException {
        // Given
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();

        Coupon coupon = new Coupon("테스트 쿠폰", CouponType.AMOUNT, 1000, null, 0, 100,
                now.minusDays(1), now.plusDays(30));
        coupon.setId(1L);

        AtomicInteger issueCount = new AtomicInteger(0);

        when(couponRepository.findByIdWithLock(1L)).thenReturn(Optional.of(coupon));
        when(userCouponRepository.existsByUserIdAndCouponId(userId, 1L)).thenReturn(false);
        when(userCouponRepository.save(any(UserCoupon.class))).thenAnswer(invocation -> {
            if (issueCount.incrementAndGet() > 1) {
                throw new DataIntegrityViolationException("Unique constraint violation");
            }
            UserCoupon userCoupon = invocation.getArgument(0);
            userCoupon.setId((long) issueCount.get());
            return userCoupon;
        });

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
                    startLatch.await();
                    issueCouponUseCase.execute(userId, coupon.getId());
                    successCount.incrementAndGet();
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

        // Then
        assertThat(successCount.get()).isEqualTo(1)
            .withFailMessage("동일 사용자는 같은 쿠폰을 1번만 발급받을 수 있어야 합니다. 성공: " + successCount.get() + ", 실패: " + failCount.get());
        assertThat(failCount.get()).isEqualTo(attempts - 1);
        assertThat(issueCount.get()).isGreaterThanOrEqualTo(1);
    }
}
