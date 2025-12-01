package org.hhplus.hhecommerce.infrastructure.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.infrastructure.config.CouponProperties;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
@ConditionalOnProperty(name = "coupon.issue.strategy", havingValue = "redis", matchIfMissing = false)
public class PendingCouponCleanupScheduler {

    private static final String PENDING_KEY_PATTERN = "coupon:pending:*";
    private static final String STOCK_KEY_PREFIX = "coupon:stock:";
    private static final String LOCK_KEY = "scheduler:pending-coupon-cleanup:lock";

    private static final long LOCK_WAIT_TIME = 0L;
    private static final long LOCK_LEASE_TIME = 60L;

    private final RedisTemplate<String, String> redisTemplate;
    private final RedissonClient redissonClient;
    private final CouponProperties couponProperties;
    private final DefaultRedisScript<Long> cleanupScript;

    public PendingCouponCleanupScheduler(RedisTemplate<String, String> redisTemplate,
                                         RedissonClient redissonClient,
                                         CouponProperties couponProperties) {
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
        this.couponProperties = couponProperties;
        this.cleanupScript = createCleanupScript();
    }

    private DefaultRedisScript<Long> createCleanupScript() {
        String script = """
            -- KEYS[1]: pending hash, KEYS[2]: stock key
            -- ARGV[1]: userId, ARGV[2]: pending timestamp, ARGV[3]: current time, ARGV[4]: timeout ms

            -- 현재 pending 값 확인 (다른 프로세스가 이미 처리했는지 체크)
            local currentPendingTime = redis.call('HGET', KEYS[1], ARGV[1])
            if currentPendingTime == false then
                return 0  -- 이미 삭제됨
            end

            -- timestamp가 일치하는지 확인 (Lua 스크립트에서 처리된 경우 다른 timestamp일 수 있음)
            if currentPendingTime ~= ARGV[2] then
                return 0  -- 다른 요청으로 갱신됨
            end

            -- 타임아웃 재확인
            local elapsed = tonumber(ARGV[3]) - tonumber(currentPendingTime)
            if elapsed <= tonumber(ARGV[4]) then
                return 0  -- 아직 타임아웃 안됨
            end

            -- 원자적으로 삭제 + 재고 복구
            redis.call('HDEL', KEYS[1], ARGV[1])
            redis.call('INCR', KEYS[2])
            return 1
            """;

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    @Scheduled(fixedRateString = "${coupon.pending.cleanup-interval-ms:60000}")
    public void cleanupStalePending() {
        RLock lock = redissonClient.getLock(LOCK_KEY);

        try {
            boolean acquired = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!acquired) {
                log.debug("다른 인스턴스에서 PENDING 정리 중. 스킵합니다.");
                return;
            }

            log.debug("오래된 PENDING 쿠폰 정리 시작");
            int cleanedCount = 0;

            ScanOptions scanOptions = ScanOptions.scanOptions()
                    .match(PENDING_KEY_PATTERN)
                    .count(100)
                    .build();

            try (Cursor<String> cursor = redisTemplate.scan(scanOptions)) {
                while (cursor.hasNext()) {
                    String pendingKey = cursor.next();
                    cleanedCount += cleanupPendingKey(pendingKey);
                }
            }

            if (cleanedCount > 0) {
                log.info("오래된 PENDING 쿠폰 정리 완료. 정리된 건수: {}", cleanedCount);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("PENDING 쿠폰 정리 중 인터럽트 발생", e);
        } catch (Exception e) {
            log.error("PENDING 쿠폰 정리 실패", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private int cleanupPendingKey(String pendingKey) {
        int cleanedCount = 0;
        long currentTime = System.currentTimeMillis();
        long cleanupTimeoutMs = couponProperties.getCleanupTimeoutMs();

        String couponId = pendingKey.replace("coupon:pending:", "");
        String stockKey = STOCK_KEY_PREFIX + couponId;

        Map<Object, Object> pendingUsers = redisTemplate.opsForHash().entries(pendingKey);

        for (Map.Entry<Object, Object> entry : pendingUsers.entrySet()) {
            String userId = entry.getKey().toString();
            String pendingTimeStr = entry.getValue().toString();
            long pendingTime;

            try {
                pendingTime = Long.parseLong(pendingTimeStr);
            } catch (NumberFormatException e) {
                log.warn("잘못된 pending 시간 형식: coupon={}, user={}", couponId, userId);
                continue;
            }

            long elapsed = currentTime - pendingTime;

            if (elapsed > cleanupTimeoutMs) {
                List<String> keys = Arrays.asList(pendingKey, stockKey);
                Long result = redisTemplate.execute(
                        cleanupScript,
                        keys,
                        userId,
                        pendingTimeStr,
                        String.valueOf(currentTime),
                        String.valueOf(cleanupTimeoutMs)
                );

                if (result != null && result == 1L) {
                    cleanedCount++;
                    log.info("Stale PENDING 정리: coupon={}, user={}, elapsed={}ms",
                            couponId, userId, elapsed);
                }
            }
        }

        return cleanedCount;
    }
}
