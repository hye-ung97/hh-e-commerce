package org.hhplus.hhecommerce.infrastructure.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 쿠폰 발급 PENDING 상태 관리를 위한 설정 클래스.
 *
 * <h3>타임아웃 설정 관계</h3>
 * <pre>
 * timeoutMs < cleanupTimeoutMs < cleanupIntervalMs (권장)
 *
 * [요청 시작] -------- timeoutMs --------> [중복 요청 허용]
 *                                              |
 *            -------- cleanupTimeoutMs ------> [스케줄러 정리 대상]
 *                                              |
 *            -------- cleanupIntervalMs -----> [다음 스케줄러 실행]
 * </pre>
 *
 * <h3>값 설정 가이드</h3>
 * <ul>
 *   <li>timeoutMs: DB 트랜잭션 최대 예상 시간의 1.5~2배</li>
 *   <li>cleanupTimeoutMs: timeoutMs의 2배 이상 (정상 트랜잭션 보호)</li>
 *   <li>cleanupIntervalMs: cleanupTimeoutMs 이상 (중복 정리 방지)</li>
 * </ul>
 */
@Slf4j
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "coupon.pending")
public class CouponProperties {

    /**
     * PENDING 상태의 최대 허용 시간 (밀리초).
     *
     * <p>이 시간 내에 동일 사용자의 중복 요청을 차단합니다.
     * Lua 스크립트에서 중복 요청 판단 시 사용됩니다.</p>
     *
     * <ul>
     *   <li>기본값: 30,000ms (30초)</li>
     *   <li>권장값: DB 트랜잭션 최대 예상 시간의 1.5~2배</li>
     * </ul>
     *
     * <p><b>주의:</b> 너무 짧으면 정상적인 재시도가 차단될 수 있고,
     * 너무 길면 실패한 요청의 재시도가 지연됩니다.</p>
     */
    private long timeoutMs = 30_000L;

    /**
     * 스케줄러가 PENDING을 정리할 때 사용하는 타임아웃 (밀리초).
     *
     * <p>이 시간이 경과한 PENDING 항목은 stale로 판단되어 정리됩니다.
     * 재고가 복구되고 해당 사용자는 다시 발급 요청이 가능해집니다.</p>
     *
     * <ul>
     *   <li>기본값: 60,000ms (60초)</li>
     *   <li>권장값: timeoutMs의 2배 이상</li>
     *   <li>필수조건: timeoutMs보다 커야 함</li>
     * </ul>
     *
     * <p><b>주의:</b></p>
     * <ul>
     *   <li>너무 짧으면: 정상 트랜잭션도 정리될 위험 (데이터 불일치)</li>
     *   <li>너무 길면: 실패한 트랜잭션의 재고가 오래 점유됨</li>
     * </ul>
     */
    private long cleanupTimeoutMs = 60_000L;

    /**
     * PENDING 정리 스케줄러 실행 주기 (밀리초).
     *
     * <p>분산 환경에서 Redisson Lock을 통해 단일 인스턴스만 실행됩니다.</p>
     *
     * <ul>
     *   <li>기본값: 60,000ms (60초)</li>
     *   <li>권장값: cleanupTimeoutMs 이상</li>
     * </ul>
     */
    private long cleanupIntervalMs = 60_000L;

    /**
     * 설정값 유효성 검증.
     *
     * <p>애플리케이션 시작 시 설정값 간의 관계를 검증합니다.</p>
     *
     * @throws IllegalStateException 설정값이 유효하지 않은 경우
     */
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

        // 경고 로그: 권장 비율 확인
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
