package org.hhplus.hhecommerce.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "coupon.pending")
public class CouponProperties {

    private long timeoutMs = 30_000L;

    private long cleanupTimeoutMs = 60_000L;

    private long cleanupIntervalMs = 60_000L;

    @PostConstruct
    public void validate() {
        if (timeoutMs <= 0) {
            throw new IllegalStateException(
                    "coupon.pending.timeoutMs는 0보다 커야 합니다. 현재값: " + timeoutMs);
        }

        if (cleanupTimeoutMs <= 0) {
            throw new IllegalStateException(
                    "coupon.pending.cleanupTimeoutMs는 0보다 커야 합니다. 현재값: " + cleanupTimeoutMs);
        }

        if (cleanupIntervalMs <= 0) {
            throw new IllegalStateException(
                    "coupon.pending.cleanupIntervalMs는 0보다 커야 합니다. 현재값: " + cleanupIntervalMs);
        }

        if (cleanupTimeoutMs <= timeoutMs) {
            throw new IllegalStateException(String.format(
                    "coupon.pending.cleanupTimeoutMs(%d)는 timeoutMs(%d)보다 커야 합니다. " +
                    "그렇지 않으면 정상적인 트랜잭션도 스케줄러에 의해 정리될 수 있습니다.",
                    cleanupTimeoutMs, timeoutMs));
        }

        if (cleanupTimeoutMs < timeoutMs * 2) {
            log.warn("cleanupTimeoutMs({})가 timeoutMs({})의 2배 미만입니다. " +
                     "네트워크 지연 등으로 정상 트랜잭션이 정리될 수 있습니다. " +
                     "권장: cleanupTimeoutMs >= {}",
                    cleanupTimeoutMs, timeoutMs, timeoutMs * 2);
        }

        log.info("쿠폰 PENDING 설정 로드 완료 - timeoutMs: {}, cleanupTimeoutMs: {}, cleanupIntervalMs: {}",
                timeoutMs, cleanupTimeoutMs, cleanupIntervalMs);
    }
}
