package org.hhplus.hhecommerce.application.coupon;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.api.dto.coupon.IssueCouponResponse;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.UserCoupon;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponErrorCode;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class IssueCouponUseCase {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final RedissonClient redissonClient;
    private final Timer lockWaitTimer;
    private final Timer lockHoldTimer;
    private final Counter lockAcquiredCounter;
    private final Counter lockFailedCounter;

    public IssueCouponUseCase(CouponRepository couponRepository,
                              UserCouponRepository userCouponRepository,
                              RedissonClient redissonClient,
                              MeterRegistry meterRegistry) {
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.redissonClient = redissonClient;

        this.lockWaitTimer = Timer.builder("coupon.lock.wait.time")
                .description("쿠폰 발급 락 획득 대기 시간")
                .tag("type", "coupon")
                .register(meterRegistry);

        this.lockHoldTimer = Timer.builder("coupon.lock.hold.time")
                .description("쿠폰 발급 락 점유 시간")
                .tag("type", "coupon")
                .register(meterRegistry);

        this.lockAcquiredCounter = Counter.builder("coupon.lock.acquired")
                .description("쿠폰 발급 락 획득 성공 횟수")
                .tag("type", "coupon")
                .register(meterRegistry);

        this.lockFailedCounter = Counter.builder("coupon.lock.failed")
                .description("쿠폰 발급 락 획득 실패 횟수")
                .tag("type", "coupon")
                .register(meterRegistry);
    }

    @Transactional
    public IssueCouponResponse execute(Long userId, Long couponId) {
        if (userCouponRepository.existsByUserIdAndCouponId(userId, couponId)) {
            log.warn("User {} already issued coupon {} (pre-check)", userId, couponId);
            throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
        }

        String lockKey = "coupon:issue:" + couponId + ":" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        long waitStartTime = System.nanoTime();

        try {
            boolean isLocked = lock.tryLock(10, 5, TimeUnit.SECONDS);
            long waitDuration = System.nanoTime() - waitStartTime;
            lockWaitTimer.record(waitDuration, TimeUnit.NANOSECONDS);

            if (!isLocked) {
                lockFailedCounter.increment();
                log.warn("Failed to acquire lock for user {} and coupon {}, waitTime: {}ms",
                        userId, couponId, TimeUnit.NANOSECONDS.toMillis(waitDuration));
                throw new CouponException(CouponErrorCode.COUPON_ISSUE_TIMEOUT);
            }

            lockAcquiredCounter.increment();
            log.debug("Lock acquired for user {} and coupon {}, waitTime: {}ms",
                    userId, couponId, TimeUnit.NANOSECONDS.toMillis(waitDuration));

            long holdStartTime = System.nanoTime();
            try {
                boolean alreadyIssued = userCouponRepository.existsByUserIdAndCouponId(userId, couponId);
                if (alreadyIssued) {
                    log.warn("User {} already issued coupon {} (post-lock check)", userId, couponId);
                    throw new CouponException(CouponErrorCode.COUPON_ALREADY_ISSUED);
                }

                Coupon coupon = couponRepository.findByIdWithLock(couponId)
                        .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

                if (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
                    log.warn("Coupon {} sold out (issued: {}, total: {})",
                            couponId, coupon.getIssuedQuantity(), coupon.getTotalQuantity());
                    throw new CouponException(CouponErrorCode.COUPON_OUT_OF_STOCK);
                }

                coupon.issue();
                couponRepository.save(coupon);

                UserCoupon userCoupon = new UserCoupon(userId, couponId, LocalDateTime.now().plusDays(30));
                userCouponRepository.save(userCoupon);

                log.info("Coupon {} issued to user {} (issued: {}/{})",
                        couponId, userId, coupon.getIssuedQuantity(), coupon.getTotalQuantity());

                return new IssueCouponResponse(
                        userCoupon.getId(),
                        userId,
                        coupon.getId(),
                        coupon.getName(),
                        coupon.getDiscountType().name(),
                        coupon.getDiscountValue(),
                        coupon.getMinOrderAmount(),
                        false,
                        userCoupon.getCreatedAt(),
                        userCoupon.getExpiredAt(),
                        "쿠폰이 발급되었습니다"
                );
            } finally {
                long holdDuration = System.nanoTime() - holdStartTime;
                lockHoldTimer.record(holdDuration, TimeUnit.NANOSECONDS);
                log.debug("Lock hold time for user {} and coupon {}, holdTime: {}ms",
                        userId, couponId, TimeUnit.NANOSECONDS.toMillis(holdDuration));
            }

        } catch (CouponException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lockFailedCounter.increment();
            log.error("Lock acquisition interrupted for user {} and coupon {}", userId, couponId, e);
            throw new CouponException(CouponErrorCode.COUPON_ISSUE_FAILED);
        } catch (Exception e) {
            log.error("Unexpected error during coupon issuance for user {} and coupon {}", userId, couponId, e);
            throw new CouponException(CouponErrorCode.COUPON_ISSUE_FAILED);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released for user {} and coupon {}", userId, couponId);
            }
        }
    }
}
