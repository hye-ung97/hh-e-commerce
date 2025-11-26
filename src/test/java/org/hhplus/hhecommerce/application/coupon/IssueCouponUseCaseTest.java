package org.hhplus.hhecommerce.application.coupon;

import org.hhplus.hhecommerce.api.dto.coupon.IssueCouponResponse;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponType;
import org.hhplus.hhecommerce.domain.coupon.UserCoupon;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponErrorCode;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IssueCouponUseCaseTest {

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @InjectMocks
    private IssueCouponUseCase issueCouponUseCase;

    @Test
    @DisplayName("정상적으로 쿠폰을 발급할 수 있다")
    void 정상적으로_쿠폰을_발급할_수_있다() throws InterruptedException {
        // Given
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        coupon.setId(1L);

        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(userCouponRepository.existsByUserIdAndCouponId(userId, 1L)).thenReturn(false);
        when(couponRepository.findByIdWithLock(1L)).thenReturn(Optional.of(coupon));
        when(couponRepository.save(any(Coupon.class))).thenReturn(coupon);
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

        verify(lock).unlock();
    }

    @Test
    @DisplayName("이미 발급받은 쿠폰은 다시 발급받을 수 없다")
    void 이미_발급받은_쿠폰은_다시_발급받을_수_없다() throws InterruptedException {
        // Given
        Long userId = 1L;
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("10% 할인", CouponType.RATE, 10, 5000, 10000, 100,
                now.minusDays(1), now.plusDays(30));
        coupon.setId(1L);

        when(userCouponRepository.existsByUserIdAndCouponId(userId, 1L)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, coupon.getId()))
            .isInstanceOf(CouponException.class)
            .hasFieldOrPropertyWithValue("errorCode", CouponErrorCode.COUPON_ALREADY_ISSUED);

        verify(redissonClient, never()).getLock(anyString());
    }

    @Test
    @DisplayName("수량이 소진된 쿠폰은 발급할 수 없다")
    void 수량이_소진된_쿠폰은_발급할_수_없다() throws InterruptedException {
        // Given
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon("한정 쿠폰", CouponType.RATE, 10, 5000, 10000, 1,
                now.minusDays(1), now.plusDays(30));
        coupon.setId(1L);
        coupon.issue(); // 1개 발급 완료 상태

        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(userCouponRepository.existsByUserIdAndCouponId(2L, 1L)).thenReturn(false);
        when(couponRepository.findByIdWithLock(1L)).thenReturn(Optional.of(coupon));

        // When & Then
        assertThatThrownBy(() -> issueCouponUseCase.execute(2L, coupon.getId()))
            .isInstanceOf(CouponException.class);

        verify(lock).unlock();
    }

    @Test
    @DisplayName("존재하지 않는 쿠폰은 발급할 수 없다")
    void 존재하지_않는_쿠폰은_발급할_수_없다() throws InterruptedException {
        // Given
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(userCouponRepository.existsByUserIdAndCouponId(1L, 999L)).thenReturn(false);
        when(couponRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> issueCouponUseCase.execute(1L, 999L))
            .isInstanceOf(CouponException.class);

        verify(lock).unlock();
    }

    @Test
    @DisplayName("락 획득에 실패하면 예외가 발생한다")
    void 락_획득_실패_시_예외_발생() throws InterruptedException {
        // Given
        Long userId = 1L;
        Long couponId = 1L;

        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> issueCouponUseCase.execute(userId, couponId))
            .isInstanceOf(CouponException.class);

        verify(lock, never()).unlock();
    }
}
