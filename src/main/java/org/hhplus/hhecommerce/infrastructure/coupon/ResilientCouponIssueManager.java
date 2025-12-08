package org.hhplus.hhecommerce.infrastructure.coupon;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueManager;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Component;

/**
 * Circuit Breaker 패턴이 적용된 쿠폰 발급 매니저.
 *
 * <p>Redis 장애 시 Circuit Breaker가 작동하여 빠른 실패(Fail-Fast)를 제공합니다.</p>
 *
 * <h3>Circuit Breaker 상태</h3>
 * <ul>
 *   <li>CLOSED: 정상 상태, 모든 요청 통과</li>
 *   <li>OPEN: 장애 감지됨, 모든 요청 즉시 거부 (fallback 실행)</li>
 *   <li>HALF_OPEN: 복구 테스트 중, 일부 요청만 허용</li>
 * </ul>
 *
 * <h3>설정값 (application.properties)</h3>
 * <ul>
 *   <li>sliding-window-size: 10 (최근 10개 요청 기준)</li>
 *   <li>failure-rate-threshold: 50% (실패율 50% 초과 시 OPEN)</li>
 *   <li>wait-duration-in-open-state: 30s (OPEN 상태 유지 시간)</li>
 *   <li>permitted-number-of-calls-in-half-open-state: 3 (HALF_OPEN에서 테스트 요청 수)</li>
 * </ul>
 */
@Slf4j
@Primary
@Component
@ConditionalOnProperty(name = "coupon.issue.strategy", havingValue = "redis", matchIfMissing = false)
public class ResilientCouponIssueManager implements CouponIssueManager {

    private static final String CIRCUIT_BREAKER_NAME = "redisCouponIssue";

    private final RedisCouponIssueManager delegate;
    private final CircuitBreaker circuitBreaker;

    public ResilientCouponIssueManager(RedisCouponIssueManager delegate,
                                        CircuitBreakerRegistry circuitBreakerRegistry) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);

        // Circuit Breaker 상태 변경 이벤트 로깅
        this.circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn(
                        "Circuit Breaker '{}' 상태 변경: {} -> {}",
                        CIRCUIT_BREAKER_NAME,
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onFailureRateExceeded(event -> log.error(
                        "Circuit Breaker '{}' 실패율 초과: {}%",
                        CIRCUIT_BREAKER_NAME,
                        event.getFailureRate()))
                .onCallNotPermitted(event -> log.warn(
                        "Circuit Breaker '{}' 요청 거부됨 (OPEN 상태)",
                        CIRCUIT_BREAKER_NAME));
    }

    @Override
    public CouponIssueResult tryIssue(Long couponId, Long userId) {
        try {
            return circuitBreaker.executeSupplier(() -> {
                CouponIssueResult result = delegate.tryIssue(couponId, userId);

                // Redis 관련 실패는 Circuit Breaker에 기록
                if (result == CouponIssueResult.ISSUE_FAILED) {
                    throw new RedisOperationException(
                            "Redis coupon issue failed for coupon " + couponId + " user " + userId);
                }

                return result;
            });
        } catch (CallNotPermittedException e) {
            // Circuit Breaker가 OPEN 상태일 때
            log.warn("Circuit breaker is OPEN - rejecting coupon issue request for coupon {} user {}",
                    couponId, userId);
            return fallback(couponId, userId, e);
        } catch (RedisOperationException e) {
            // Redis 작업 실패 (Circuit Breaker에 기록됨)
            log.error("Redis operation failed for coupon {} user {}: {}",
                    couponId, userId, e.getMessage());
            return CouponIssueResult.ISSUE_FAILED;
        } catch (RedisConnectionFailureException e) {
            // Redis 연결 실패
            log.error("Redis connection failed for coupon {} user {}: {}",
                    couponId, userId, e.getMessage());
            return CouponIssueResult.ISSUE_FAILED;
        } catch (Exception e) {
            // 기타 예외
            log.error("Unexpected error during coupon issue for coupon {} user {}",
                    couponId, userId, e);
            return CouponIssueResult.ISSUE_FAILED;
        }
    }

    /**
     * Circuit Breaker가 OPEN 상태일 때 실행되는 fallback 메서드.
     *
     * <p>현재는 즉시 거부 전략을 사용합니다.
     * 필요에 따라 다음 전략으로 확장할 수 있습니다:</p>
     * <ul>
     *   <li>DB 직접 조회 후 발급 (성능 저하 감수)</li>
     *   <li>Queue에 저장 후 비동기 처리</li>
     *   <li>로컬 캐시 활용</li>
     * </ul>
     */
    private CouponIssueResult fallback(Long couponId, Long userId, Throwable throwable) {
        log.warn("Fallback triggered for coupon {} user {} due to: {}",
                couponId, userId, throwable.getClass().getSimpleName());

        // 전략 1: 즉시 거부 (현재 구현)
        // 사용자에게 잠시 후 재시도를 안내
        return CouponIssueResult.LOCK_ACQUISITION_FAILED;

        // 전략 2: DB Fallback (주석 처리됨 - 필요 시 활성화)
        // return directDatabaseIssue(couponId, userId);
    }

    @Override
    public void confirm(Long couponId, Long userId) {
        try {
            circuitBreaker.executeRunnable(() -> delegate.confirm(couponId, userId));
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker is OPEN - confirm will be retried later for coupon {} user {}",
                    couponId, userId);
            // confirm은 이벤트 기반으로 재시도되므로 예외를 던지지 않음
        } catch (Exception e) {
            log.error("Failed to confirm coupon {} for user {}", couponId, userId, e);
        }
    }

    @Override
    public void rollback(Long couponId, Long userId) {
        try {
            circuitBreaker.executeRunnable(() -> delegate.rollback(couponId, userId));
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker is OPEN - rollback will be handled by cleanup scheduler for coupon {} user {}",
                    couponId, userId);
            // rollback은 스케줄러가 정리하므로 예외를 던지지 않음
        } catch (Exception e) {
            log.error("Failed to rollback coupon {} for user {}", couponId, userId, e);
        }
    }

    @Override
    public boolean hasAlreadyIssued(Long couponId, Long userId) {
        try {
            return circuitBreaker.executeSupplier(() -> delegate.hasAlreadyIssued(couponId, userId));
        } catch (CallNotPermittedException e) {
            log.warn("Circuit breaker is OPEN - cannot check issued status for coupon {} user {}",
                    couponId, userId);
            // 안전하게 false 반환 (이미 발급됐는지 확인 불가)
            return false;
        } catch (Exception e) {
            log.error("Failed to check issued status for coupon {} user {}", couponId, userId, e);
            return false;
        }
    }

    @Override
    public boolean shouldUpdateCouponStock() {
        return delegate.shouldUpdateCouponStock();
    }

    /**
     * Circuit Breaker 상태 정보를 반환합니다.
     * 모니터링 및 헬스 체크에 사용됩니다.
     */
    public CircuitBreakerStatus getCircuitBreakerStatus() {
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();
        return new CircuitBreakerStatus(
                circuitBreaker.getState().name(),
                metrics.getFailureRate(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfSuccessfulCalls(),
                metrics.getNumberOfNotPermittedCalls()
        );
    }

    /**
     * Circuit Breaker 상태 정보를 담는 record.
     */
    public record CircuitBreakerStatus(
            String state,
            float failureRate,
            int failedCalls,
            int successfulCalls,
            long notPermittedCalls
    ) {}

    /**
     * Redis 작업 실패를 나타내는 예외.
     * Circuit Breaker에서 실패로 기록됩니다.
     */
    public static class RedisOperationException extends RuntimeException {
        public RedisOperationException(String message) {
            super(message);
        }

        public RedisOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
