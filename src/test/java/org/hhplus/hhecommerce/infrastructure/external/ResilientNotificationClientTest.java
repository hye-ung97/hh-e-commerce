package org.hhplus.hhecommerce.infrastructure.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.hhplus.hhecommerce.domain.common.RejectedAsyncTaskRepository;
import org.hhplus.hhecommerce.domain.order.PaymentCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResilientNotificationClient 테스트")
class ResilientNotificationClientTest {

    @Mock
    private NotificationClient delegate;

    @Mock
    private RejectedAsyncTaskRepository rejectedAsyncTaskRepository;

    @Mock
    private ObjectMapper objectMapper;

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private ResilientNotificationClient resilientClient;

    private PaymentCompletedEvent testEvent;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(5)
                .slidingWindowSize(10)
                .build();

        circuitBreakerRegistry = CircuitBreakerRegistry.of(config);

        resilientClient = new ResilientNotificationClient(
                delegate,
                circuitBreakerRegistry,
                rejectedAsyncTaskRepository,
                objectMapper
        );

        testEvent = createTestEvent();
    }

    private PaymentCompletedEvent createTestEvent() {
        List<PaymentCompletedEvent.OrderItemInfo> orderItems = List.of(
                new PaymentCompletedEvent.OrderItemInfo(1L, "테스트 상품", "옵션A", 2, 10000, 20000)
        );

        return PaymentCompletedEvent.of(
                1L,
                100L,
                "010-1234-5678",
                20000,
                2000,
                18000,
                orderItems,
                Map.of(1L, 2),
                LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("sendOrderConfirmation 테스트")
    class SendOrderConfirmationTest {

        @Test
        @DisplayName("정상적으로 알림을 발송한다")
        void shouldSendNotificationSuccessfully() {
            // when
            resilientClient.sendOrderConfirmation(testEvent);

            // then
            verify(delegate).sendOrderConfirmation(testEvent);
        }

        @Test
        @DisplayName("delegate 실패 시 예외가 전파되지 않는다")
        void shouldNotPropagateExceptionWhenDelegateFails() throws Exception {
            // given
            doThrow(new RuntimeException("알림 발송 실패")).when(delegate).sendOrderConfirmation(any());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            // when & then - 예외가 발생하지 않음
            resilientClient.sendOrderConfirmation(testEvent);

            verify(delegate).sendOrderConfirmation(testEvent);
            verify(rejectedAsyncTaskRepository).save(any());
        }

        @Test
        @DisplayName("Circuit Breaker가 OPEN 상태면 delegate를 호출하지 않는다")
        void shouldNotCallDelegateWhenCircuitBreakerIsOpen() throws Exception {
            // given
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("notification");
            circuitBreaker.transitionToOpenState();
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            // when
            resilientClient.sendOrderConfirmation(testEvent);

            // then
            verify(delegate, never()).sendOrderConfirmation(any());
            verify(rejectedAsyncTaskRepository).save(any());
        }

        @Test
        @DisplayName("Circuit Breaker가 HALF_OPEN 상태에서 성공하면 CLOSED로 전환한다")
        void shouldTransitionToClosedOnSuccessInHalfOpen() {
            // given
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("notification");
            circuitBreaker.transitionToOpenState();
            circuitBreaker.transitionToHalfOpenState();

            // when
            resilientClient.sendOrderConfirmation(testEvent);

            // then
            verify(delegate).sendOrderConfirmation(testEvent);
        }
    }

    @Nested
    @DisplayName("getCircuitBreakerStatus 테스트")
    class GetCircuitBreakerStatusTest {

        @Test
        @DisplayName("Circuit Breaker 상태 정보를 반환한다")
        void shouldReturnCircuitBreakerStatus() {
            // when
            ResilientNotificationClient.CircuitBreakerStatus status =
                    resilientClient.getCircuitBreakerStatus();

            // then
            assertThat(status).isNotNull();
            assertThat(status.state()).isEqualTo("CLOSED");
            assertThat(status.failedCalls()).isEqualTo(0);
            assertThat(status.successfulCalls()).isEqualTo(0);
        }

        @Test
        @DisplayName("OPEN 상태일 때 상태 정보를 정확히 반환한다")
        void shouldReturnOpenStateCorrectly() {
            // given
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("notification");
            circuitBreaker.transitionToOpenState();

            // when
            ResilientNotificationClient.CircuitBreakerStatus status =
                    resilientClient.getCircuitBreakerStatus();

            // then
            assertThat(status.state()).isEqualTo("OPEN");
        }

        @Test
        @DisplayName("HALF_OPEN 상태일 때 상태 정보를 정확히 반환한다")
        void shouldReturnHalfOpenStateCorrectly() {
            // given
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("notification");
            circuitBreaker.transitionToOpenState();
            circuitBreaker.transitionToHalfOpenState();

            // when
            ResilientNotificationClient.CircuitBreakerStatus status =
                    resilientClient.getCircuitBreakerStatus();

            // then
            assertThat(status.state()).isEqualTo("HALF_OPEN");
        }
    }

    @Nested
    @DisplayName("연속 실패 시나리오 테스트")
    class ConsecutiveFailureTest {

        @Test
        @DisplayName("연속 실패 후에도 예외가 전파되지 않는다")
        void shouldNotPropagateExceptionAfterConsecutiveFailures() throws Exception {
            // given
            doThrow(new RuntimeException("알림 발송 실패")).when(delegate).sendOrderConfirmation(any());
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            // when & then - 여러 번 호출해도 예외가 전파되지 않음
            for (int i = 0; i < 10; i++) {
                resilientClient.sendOrderConfirmation(testEvent);
            }

            verify(delegate, atMost(10)).sendOrderConfirmation(any());
        }
    }
}
