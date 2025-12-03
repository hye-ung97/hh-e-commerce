package org.hhplus.hhecommerce.infrastructure.coupon;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.coupon.Coupon;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueManager;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueResult;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(name = "coupon.issue.strategy", havingValue = "lock", matchIfMissing = true)
public class RedissonLockCouponIssueManager implements CouponIssueManager {

    private static final String LOCK_KEY_PREFIX = "coupon:issue:";
    private static final long LOCK_WAIT_TIME = 10L;
    private static final long LOCK_LEASE_TIME = 5L;

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final RedissonClient redissonClient;
    private final Timer lockWaitTimer;
    private final Timer lockHoldTimer;
    private final Counter lockAcquiredCounter;
    private final Counter lockFailedCounter;

    public RedissonLockCouponIssueManager(CouponRepository couponRepository,
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

    @Override
    public CouponIssueResult tryIssue(Long couponId, Long userId) {
        if (hasAlreadyIssued(couponId, userId)) {
            log.warn("User {} already issued coupon {} (pre-check)", userId, couponId);
            return CouponIssueResult.ALREADY_ISSUED;
        }

        String lockKey = LOCK_KEY_PREFIX + couponId + ":" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        long waitStartTime = System.nanoTime();

        try {
            boolean isLocked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            long waitDuration = System.nanoTime() - waitStartTime;
            lockWaitTimer.record(waitDuration, TimeUnit.NANOSECONDS);

            if (!isLocked) {
                lockFailedCounter.increment();
                log.warn("Failed to acquire lock for user {} and coupon {}, waitTime: {}ms",
                        userId, couponId, TimeUnit.NANOSECONDS.toMillis(waitDuration));
                return CouponIssueResult.LOCK_ACQUISITION_FAILED;
            }

            lockAcquiredCounter.increment();
            log.debug("Lock acquired for user {} and coupon {}, waitTime: {}ms",
                    userId, couponId, TimeUnit.NANOSECONDS.toMillis(waitDuration));

            long holdStartTime = System.nanoTime();
            try {
                return executeIssue(couponId, userId);
            } finally {
                long holdDuration = System.nanoTime() - holdStartTime;
                lockHoldTimer.record(holdDuration, TimeUnit.NANOSECONDS);
                log.debug("Lock hold time for user {} and coupon {}, holdTime: {}ms",
                        userId, couponId, TimeUnit.NANOSECONDS.toMillis(holdDuration));
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lockFailedCounter.increment();
            log.error("Lock acquisition interrupted for user {} and coupon {}", userId, couponId, e);
            return CouponIssueResult.ISSUE_FAILED;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released for user {} and coupon {}", userId, couponId);
            }
        }
    }

    private CouponIssueResult executeIssue(Long couponId, Long userId) {
        if (hasAlreadyIssued(couponId, userId)) {
            log.warn("User {} already issued coupon {} (post-lock check)", userId, couponId);
            return CouponIssueResult.ALREADY_ISSUED;
        }

        Optional<Coupon> couponOpt = couponRepository.findByIdWithLock(couponId);
        if (couponOpt.isEmpty()) {
            return CouponIssueResult.COUPON_NOT_FOUND;
        }

        Coupon coupon = couponOpt.get();

        if (coupon.getIssuedQuantity() >= coupon.getTotalQuantity()) {
            log.warn("Coupon {} sold out (issued: {}, total: {})",
                    couponId, coupon.getIssuedQuantity(), coupon.getTotalQuantity());
            return CouponIssueResult.OUT_OF_STOCK;
        }

        if (!coupon.canIssue()) {
            return CouponIssueResult.NOT_AVAILABLE;
        }

        coupon.issue();
        couponRepository.save(coupon);

        log.info("Coupon {} issued to user {} (issued: {}/{})",
                couponId, userId, coupon.getIssuedQuantity(), coupon.getTotalQuantity());

        return CouponIssueResult.SUCCESS;
    }

    @Override
    public boolean hasAlreadyIssued(Long couponId, Long userId) {
        return userCouponRepository.existsByUserIdAndCouponId(userId, couponId);
    }

    @Override
    public void confirm(Long couponId, Long userId) {
        log.debug("Confirm called for coupon {} user {} (no-op for lock strategy)", couponId, userId);
    }

    @Override
    public void rollback(Long couponId, Long userId) {
        log.debug("Rollback called for coupon {} user {} (no-op for lock strategy)", couponId, userId);
    }

    @Override
    public boolean shouldUpdateCouponStock() {
        return false;
    }
}
