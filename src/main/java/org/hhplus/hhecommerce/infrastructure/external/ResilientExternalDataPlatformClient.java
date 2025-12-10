package org.hhplus.hhecommerce.infrastructure.external;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.order.FailedDataPlatformEvent;
import org.hhplus.hhecommerce.domain.order.FailedDataPlatformEventRepository;
import org.hhplus.hhecommerce.domain.order.PaymentCompletedEvent;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Primary
@Component
public class ResilientExternalDataPlatformClient {

    private static final String CIRCUIT_BREAKER_NAME = "dataPlatform";

    private final ExternalDataPlatformClient delegate;
    private final CircuitBreaker circuitBreaker;
    private final FailedDataPlatformEventRepository failedEventRepository;
    private final ObjectMapper objectMapper;

    public ResilientExternalDataPlatformClient(
            ExternalDataPlatformClient delegate,
            CircuitBreakerRegistry circuitBreakerRegistry,
            FailedDataPlatformEventRepository failedEventRepository,
            ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(CIRCUIT_BREAKER_NAME);
        this.failedEventRepository = failedEventRepository;
        this.objectMapper = objectMapper;

        registerEventListeners();
    }

    private void registerEventListeners() {
        this.circuitBreaker.getEventPublisher()
                .onStateTransition(event -> log.warn(
                        "[DataPlatform] Circuit Breaker 상태 변경: {} -> {}",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()))
                .onFailureRateExceeded(event -> log.error(
                        "[DataPlatform] Circuit Breaker 실패율 초과: {}%",
                        event.getFailureRate()))
                .onCallNotPermitted(event -> log.warn(
                        "[DataPlatform] Circuit Breaker 요청 거부됨 (OPEN 상태)"))
                .onSuccess(event -> log.debug(
                        "[DataPlatform] 요청 성공 - duration: {}ms",
                        event.getElapsedDuration().toMillis()))
                .onError(event -> log.warn(
                        "[DataPlatform] 요청 실패 - duration: {}ms, error: {}",
                        event.getElapsedDuration().toMillis(),
                        event.getThrowable().getMessage()));
    }

    public void sendOrderData(PaymentCompletedEvent event) {
        try {
            circuitBreaker.executeRunnable(() -> delegate.sendOrderData(event));
            log.debug("[DataPlatform] 전송 성공 - orderId: {}", event.orderId());
        } catch (CallNotPermittedException e) {
            log.warn("[DataPlatform] Circuit Breaker OPEN - 요청 거부. orderId: {}", event.orderId());
            fallback(event, e);
        } catch (Exception e) {
            log.error("[DataPlatform] 전송 실패 - orderId: {}, error: {}",
                    event.orderId(), e.getMessage());
            fallback(event, e);
        }
    }

    private void fallback(PaymentCompletedEvent event, Throwable throwable) {
        log.warn("[DataPlatform] Fallback 실행 - orderId: {}, reason: {}",
                event.orderId(), throwable.getClass().getSimpleName());

        try {
            String eventPayload = objectMapper.writeValueAsString(event);
            FailedDataPlatformEvent failedEvent = new FailedDataPlatformEvent(
                    event.orderId(),
                    event.userId(),
                    eventPayload,
                    throwable.getMessage()
            );
            failedEventRepository.save(failedEvent);
            log.info("[DataPlatform] 실패 이벤트 저장 완료 - orderId: {}", event.orderId());
        } catch (JsonProcessingException e) {
            log.error("[DataPlatform] 이벤트 직렬화 실패 - orderId: {}, error: {}",
                    event.orderId(), e.getMessage());
        } catch (Exception e) {
            log.error("[DataPlatform] 실패 이벤트 저장 실패 - orderId: {}, error: {}",
                    event.orderId(), e.getMessage());
        }
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
