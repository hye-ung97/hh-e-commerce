package org.hhplus.hhecommerce.infrastructure.external;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.order.PaymentCompletedEvent;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Primary
@Component
public class ResilientNotificationClient {

    private static final String CIRCUIT_BREAKER_NAME = "notification";

    private final NotificationClient delegate;
    private final CircuitBreaker circuitBreaker;

    public ResilientNotificationClient(
            NotificationClient delegate,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);

        registerEventListeners();
    }

    private void registerEventListeners() {
        this.circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn(
                        "[Notification] Circuit Breaker 상태 변경: {} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onFailureRateExceeded(event -> log.error(
                        "[Notification] Circuit Breaker 실패율 초과: {}%",
                        event.getFailureRate()))
                .onCallNotPermitted(event -> log.warn(
                        "[Notification] Circuit Breaker 요청 거부됨 (OPEN 상태)"))
                .onSuccess(event -> log.debug(
                        "[Notification] 알림 발송 성공 - duration: {}ms",
                        event.getElapsedDuration().toMillis()))
                .onError(event -> log.warn(
                        "[Notification] 알림 발송 실패 - duration: {}ms, error: {}",
                        event.getElapsedDuration().toMillis(),
                        event.getThrowable().getMessage()));
    }

    public void sendOrderConfirmation(PaymentCompletedEvent event) {
        try {
            circuitBreaker.executeRunnable(() -> delegate.sendOrderConfirmation(event));
            log.debug("[Notification] 알림 발송 성공 - orderId: {}", event.orderId());
        } catch (CallNotPermittedException e) {
            log.warn("[Notification] Circuit Breaker OPEN - 알림 발송 생략. orderId: {}", event.orderId());
            fallback(event, e);
        } catch (Exception e) {
            log.error("[Notification] 알림 발송 최종 실패 - orderId: {}, error: {}",
                    event.orderId(), e.getMessage());
            fallback(event, e);
        }
    }

    private void fallback(PaymentCompletedEvent event, Throwable throwable) {
        log.warn("[Notification] Fallback 실행 - orderId: {}, userId: {}, reason: {}",
                event.orderId(), event.userId(), throwable.getClass().getSimpleName());

    }

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

    public record CircuitBreakerStatus(
            String state,
            float failureRate,
            int failedCalls,
            int successfulCalls,
            long notPermittedCalls
    ) {}
}
