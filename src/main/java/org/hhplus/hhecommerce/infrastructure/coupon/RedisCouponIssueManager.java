package org.hhplus.hhecommerce.infrastructure.coupon;

import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueManager;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueResult;
import org.hhplus.hhecommerce.domain.coupon.CouponRepository;
import org.hhplus.hhecommerce.domain.coupon.UserCouponRepository;
import org.hhplus.hhecommerce.infrastructure.config.CouponProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Primary
@Component
@ConditionalOnProperty(name = "coupon.issue.strategy", havingValue = "redis", matchIfMissing = false)
public class RedisCouponIssueManager implements CouponIssueManager {

    private static final String STOCK_KEY_PREFIX = "coupon:stock:";
    private static final String ISSUED_KEY_PREFIX = "coupon:issued:";
    private static final String PENDING_KEY_PREFIX = "coupon:pending:";
    private static final String INIT_LOCK_KEY_PREFIX = "coupon:init:lock:";
    private static final String INIT_COMPLETE_KEY_PREFIX = "coupon:init:complete:";
    private static final Duration DEFAULT_TTL = Duration.ofDays(31);
    private static final Duration INIT_LOCK_TTL = Duration.ofSeconds(10);
    private static final int INIT_WAIT_MAX_RETRIES = 50;
    private static final long INIT_WAIT_INTERVAL_MS = 100;

    private static final Long RESULT_SUCCESS = 1L;
    private static final Long RESULT_ALREADY_ISSUED = -1L;
    private static final Long RESULT_OUT_OF_STOCK = -2L;
    private static final Long RESULT_NOT_INITIALIZED = -3L;
    private static final Long RESULT_PENDING_IN_PROGRESS = -4L;

    private final RedisTemplate<String, String> redisTemplate;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponProperties couponProperties;
    private final DefaultRedisScript<Long> issueScript;
    private final DefaultRedisScript<Long> confirmScript;
    private final DefaultRedisScript<Long> rollbackScript;

    public RedisCouponIssueManager(RedisTemplate<String, String> redisTemplate,
                                   CouponRepository couponRepository,
                                   UserCouponRepository userCouponRepository,
                                   CouponProperties couponProperties) {
        this.redisTemplate = redisTemplate;
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.couponProperties = couponProperties;
        this.issueScript = createIssueScript();
        this.confirmScript = createConfirmScript();
        this.rollbackScript = createRollbackScript();
    }

    private DefaultRedisScript<Long> createIssueScript() {
        String script = """
            -- KEYS[1]: stock key, KEYS[2]: issued set, KEYS[3]: pending hash
            -- ARGV[1]: userId, ARGV[2]: current timestamp, ARGV[3]: pending timeout ms

            -- 1. 이미 발급 완료 체크
            if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
                return -1
            end

            -- 2. 이미 예약 중인지 체크 (타임아웃 내 중복 요청 방지)
            local pendingTime = redis.call('HGET', KEYS[3], ARGV[1])
            if pendingTime then
                local elapsed = tonumber(ARGV[2]) - tonumber(pendingTime)
                if elapsed < tonumber(ARGV[3]) then
                    return -4
                end
                -- 타임아웃된 pending은 정리하고 재시도 허용
                redis.call('HDEL', KEYS[3], ARGV[1])
                redis.call('INCR', KEYS[1])
            end

            -- 3. 재고 키 존재 여부 확인
            local stock = redis.call('GET', KEYS[1])
            if stock == false then
                return -3
            end

            -- 4. 재고 확인
            if tonumber(stock) <= 0 then
                return -2
            end

            -- 5. 재고 감소 (음수 방지)
            local newStock = redis.call('DECR', KEYS[1])
            if newStock < 0 then
                redis.call('INCR', KEYS[1])
                return -2
            end

            -- 6. PENDING 상태로 기록 (issued가 아닌 pending에)
            redis.call('HSET', KEYS[3], ARGV[1], ARGV[2])

            return 1
            """;

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    private DefaultRedisScript<Long> createConfirmScript() {
        String script = """
            -- KEYS[1]: issued set, KEYS[2]: pending hash
            -- ARGV[1]: userId

            -- pending에서 제거하고 issued로 이동
            local removed = redis.call('HDEL', KEYS[2], ARGV[1])
            if removed == 1 then
                redis.call('SADD', KEYS[1], ARGV[1])
                return 1
            end
            return 0
            """;

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    private DefaultRedisScript<Long> createRollbackScript() {
        String script = """
            -- KEYS[1]: stock key, KEYS[2]: pending hash
            -- ARGV[1]: userId

            -- pending에서 제거하고 재고 복구
            local removed = redis.call('HDEL', KEYS[2], ARGV[1])
            if removed == 1 then
                redis.call('INCR', KEYS[1])
                return 1
            end
            return 0
            """;

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptText(script);
        redisScript.setResultType(Long.class);
        return redisScript;
    }

    @Override
    public CouponIssueResult tryIssue(Long couponId, Long userId) {
        String stockKey = STOCK_KEY_PREFIX + couponId;
        String issuedKey = ISSUED_KEY_PREFIX + couponId;
        String pendingKey = PENDING_KEY_PREFIX + couponId;

        try {
            List<String> keys = Arrays.asList(stockKey, issuedKey, pendingKey);
            String timestamp = String.valueOf(System.currentTimeMillis());
            String timeoutMs = String.valueOf(couponProperties.getTimeoutMs());

            Long result = redisTemplate.execute(issueScript, keys, userId.toString(), timestamp, timeoutMs);

            if (result == null) {
                log.error("Lua script returned null for coupon {} user {}", couponId, userId);
                return CouponIssueResult.ISSUE_FAILED;
            }

            if (result.equals(RESULT_NOT_INITIALIZED)) {
                log.info("Coupon {} not initialized in Redis, syncing from DB", couponId);
                if (syncFromDatabase(couponId)) {
                    result = redisTemplate.execute(issueScript, keys, userId.toString(), timestamp, timeoutMs);
                    if (result == null) {
                        return CouponIssueResult.ISSUE_FAILED;
                    }
                } else {
                    return CouponIssueResult.COUPON_NOT_FOUND;
                }
            }

            return mapResult(result, couponId, userId);
        } catch (Exception e) {
            log.error("Redis coupon issue failed for coupon {} user {}", couponId, userId, e);
            return CouponIssueResult.ISSUE_FAILED;
        }
    }

    @Override
    public void confirm(Long couponId, Long userId) {
        String issuedKey = ISSUED_KEY_PREFIX + couponId;
        String pendingKey = PENDING_KEY_PREFIX + couponId;

        try {
            List<String> keys = Arrays.asList(issuedKey, pendingKey);
            Long result = redisTemplate.execute(confirmScript, keys, userId.toString());

            if (result != null && result == 1L) {
                log.info("Confirmed coupon {} for user {}", couponId, userId);
            } else {
                // pending에 없음 → issued 상태 확인 (멱등성 보장)
                Boolean alreadyIssued = redisTemplate.opsForSet()
                        .isMember(issuedKey, userId.toString());

                if (Boolean.TRUE.equals(alreadyIssued)) {
                    log.info("Already confirmed coupon {} for user {} (idempotent)", couponId, userId);
                } else {
                    // issued에도 없음 → 강제 추가 (DB에는 저장됐으므로 복구)
                    redisTemplate.opsForSet().add(issuedKey, userId.toString());
                    // TTL이 없을 수 있으므로 설정 (이미 있으면 갱신)
                    redisTemplate.expire(issuedKey, DEFAULT_TTL);
                    log.warn("Force confirmed coupon {} for user {} (recovery)", couponId, userId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to confirm coupon {} for user {}", couponId, userId, e);
        }
    }

    @Override
    public void rollback(Long couponId, Long userId) {
        String stockKey = STOCK_KEY_PREFIX + couponId;
        String pendingKey = PENDING_KEY_PREFIX + couponId;

        try {
            List<String> keys = Arrays.asList(stockKey, pendingKey);
            Long result = redisTemplate.execute(rollbackScript, keys, userId.toString());

            if (result != null && result == 1L) {
                log.info("Rolled back coupon {} for user {}", couponId, userId);
            } else {
                log.warn("Rollback skipped - pending not found for coupon {} user {}", couponId, userId);
            }
        } catch (Exception e) {
            log.error("Failed to rollback coupon {} for user {}", couponId, userId, e);
        }
    }

    private boolean syncFromDatabase(Long couponId) {
        if (isInitializationComplete(couponId)) {
            return true;
        }

        String lockKey = INIT_LOCK_KEY_PREFIX + couponId;

        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", INIT_LOCK_TTL);

        if (!Boolean.TRUE.equals(acquired)) {
            log.info("Another thread is initializing coupon {}, waiting...", couponId);
            return waitForInitialization(couponId);
        }

        try {
            if (isInitializationComplete(couponId)) {
                log.info("Coupon {} already initialized by another thread", couponId);
                return true;
            }

            return doSyncFromDatabase(couponId);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private boolean waitForInitialization(Long couponId) {
        for (int i = 0; i < INIT_WAIT_MAX_RETRIES; i++) {
            try {
                Thread.sleep(INIT_WAIT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for coupon {} initialization", couponId);
                return false;
            }

            if (isInitializationComplete(couponId)) {
                log.info("Coupon {} initialization completed by another thread", couponId);
                return true;
            }
        }

        log.warn("Timeout waiting for coupon {} initialization", couponId);
        return false;
    }

    private boolean doSyncFromDatabase(Long couponId) {
        return couponRepository.findById(couponId)
                .map(coupon -> {
                    int remainingStock = coupon.getTotalQuantity() - coupon.getIssuedQuantity();

                    syncIssuedUsers(couponId);
                    initializeStock(couponId, remainingStock);
                    markInitializationComplete(couponId);

                    log.info("Successfully synced coupon {} from database (stock: {})", couponId, remainingStock);
                    return true;
                })
                .orElse(false);
    }

    private boolean isInitializationComplete(Long couponId) {
        String completeKey = INIT_COMPLETE_KEY_PREFIX + couponId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(completeKey));
    }

    private void markInitializationComplete(Long couponId) {
        String completeKey = INIT_COMPLETE_KEY_PREFIX + couponId;
        redisTemplate.opsForValue().set(completeKey, "1", DEFAULT_TTL);
    }

    private void syncIssuedUsers(Long couponId) {
        String issuedKey = ISSUED_KEY_PREFIX + couponId;

        List<Long> issuedUserIds = userCouponRepository.findByCouponId(couponId)
                .stream()
                .map(uc -> uc.getUserId())
                .toList();

        if (!issuedUserIds.isEmpty()) {
            String[] userIdStrings = issuedUserIds.stream()
                    .map(String::valueOf)
                    .toArray(String[]::new);
            redisTemplate.opsForSet().add(issuedKey, userIdStrings);
            redisTemplate.expire(issuedKey, DEFAULT_TTL);
            log.info("Synced {} issued users for coupon {}", issuedUserIds.size(), couponId);
        }
    }

    private CouponIssueResult mapResult(Long result, Long couponId, Long userId) {
        if (result.equals(RESULT_SUCCESS)) {
            log.info("Coupon {} reserved (pending) for user {}", couponId, userId);
            return CouponIssueResult.SUCCESS;
        } else if (result.equals(RESULT_ALREADY_ISSUED)) {
            log.warn("User {} already issued coupon {}", userId, couponId);
            return CouponIssueResult.ALREADY_ISSUED;
        } else if (result.equals(RESULT_OUT_OF_STOCK)) {
            log.warn("Coupon {} out of stock", couponId);
            return CouponIssueResult.OUT_OF_STOCK;
        } else if (result.equals(RESULT_PENDING_IN_PROGRESS)) {
            log.warn("User {} has pending request for coupon {}", userId, couponId);
            return CouponIssueResult.PENDING_IN_PROGRESS;
        } else {
            log.error("Unknown result {} for coupon {} user {}", result, couponId, userId);
            return CouponIssueResult.ISSUE_FAILED;
        }
    }

    @Override
    public boolean hasAlreadyIssued(Long couponId, Long userId) {
        String issuedKey = ISSUED_KEY_PREFIX + couponId;
        Boolean isMember = redisTemplate.opsForSet().isMember(issuedKey, userId.toString());
        return Boolean.TRUE.equals(isMember);
    }

    public void initializeStock(Long couponId, int quantity) {
        String stockKey = STOCK_KEY_PREFIX + couponId;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(quantity), DEFAULT_TTL);
        log.info("Initialized coupon {} stock to {}", couponId, quantity);
    }

    public int getRemainingStock(Long couponId) {
        String stockKey = STOCK_KEY_PREFIX + couponId;
        String stock = redisTemplate.opsForValue().get(stockKey);
        return stock != null ? Integer.parseInt(stock) : 0;
    }

    public long getIssuedCount(Long couponId) {
        String issuedKey = ISSUED_KEY_PREFIX + couponId;
        Long size = redisTemplate.opsForSet().size(issuedKey);
        return size != null ? size : 0;
    }

    public Map<Object, Object> getPendingUsers(Long couponId) {
        String pendingKey = PENDING_KEY_PREFIX + couponId;
        return redisTemplate.opsForHash().entries(pendingKey);
    }

    public boolean hasStockKey(Long couponId) {
        String stockKey = STOCK_KEY_PREFIX + couponId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(stockKey));
    }

    @Override
    public boolean shouldUpdateCouponStock() {
        return true;
    }
}
