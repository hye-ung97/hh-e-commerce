package org.hhplus.hhecommerce.infrastructure.external;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.hhplus.hhecommerce.domain.order.FailedDataPlatformEvent;
import org.hhplus.hhecommerce.domain.order.FailedDataPlatformEventRepository;
import org.hhplus.hhecommerce.domain.order.PaymentCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResilientExternalDataPlatformClient 테스트")
class ResilientExternalDataPlatformClientTest {

    @Mock
    private ExternalDataPlatformClient delegate;

    @Mock
    private FailedDataPlatformEventRepository failedEventRepository;

    private ObjectMapper objectMapper;
    private CircuitBreakerRegistry circuitBreakerRegistry;
    private ResilientExternalDataPlatformClient resilientClient;

    private PaymentCompletedEvent testEvent;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .minimumNumberOfCalls(5)
                .slidingWindowSize(10)
                .build();

        circuitBreakerRegistry = CircuitBreakerRegistry.of(config);

        resilientClient = new ResilientExternalDataPlatformClient(
                delegate,
                circuitBreakerRegistry,
                failedEventRepository,
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
    @DisplayName("sendOrderData 테스트")
    class SendOrderDataTest {

        @Test
        @DisplayName("정상적으로 데이터를 전송한다")
        void shouldSendDataSuccessfully() {
            // when
            resilientClient.sendOrderData(testEvent);

            // then
            verify(delegate).sendOrderData(testEvent);
            verify(failedEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("delegate 실패 시 fallback으로 실패 이벤트를 저장한다")
        void shouldSaveFailedEventWhenDelegateFails() {
            // given
            doThrow(new RuntimeException("전송 실패")).when(delegate).sendOrderData(any());

            // when
            resilientClient.sendOrderData(testEvent);

            // then
            verify(delegate).sendOrderData(testEvent);
            ArgumentCaptor<FailedDataPlatformEvent> captor = ArgumentCaptor.forClass(FailedDataPlatformEvent.class);
            verify(failedEventRepository).save(captor.capture());

            FailedDataPlatformEvent savedEvent = captor.getValue();
            assertThat(savedEvent.getOrderId()).isEqualTo(testEvent.orderId());
            assertThat(savedEvent.getUserId()).isEqualTo(testEvent.userId());
        }

        @Test
        @DisplayName("Circuit Breaker가 OPEN 상태면 delegate를 호출하지 않고 fallback을 실행한다")
        void shouldExecuteFallbackWhenCircuitBreakerIsOpen() {
            // given
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("dataPlatform");
            circuitBreaker.transitionToOpenState();

            // when
            resilientClient.sendOrderData(testEvent);

            // then
            verify(delegate, never()).sendOrderData(any());
            verify(failedEventRepository).save(any(FailedDataPlatformEvent.class));
        }
    }

    @Nested
    @DisplayName("getCircuitBreakerStatus 테스트")
    class GetCircuitBreakerStatusTest {

        @Test
        @DisplayName("Circuit Breaker 상태 정보를 반환한다")
        void shouldReturnCircuitBreakerStatus() {
            // when
            ResilientExternalDataPlatformClient.CircuitBreakerStatus status =
                    resilientClient.getCircuitBreakerStatus();

            // then
            assertThat(status).isNotNull();
            assertThat(status.state()).isEqualTo("CLOSED");
        }

        @Test
        @DisplayName("OPEN 상태일 때 상태 정보를 정확히 반환한다")
        void shouldReturnOpenStateCorrectly() {
            // given
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("dataPlatform");
            circuitBreaker.transitionToOpenState();

            // when
            ResilientExternalDataPlatformClient.CircuitBreakerStatus status =
                    resilientClient.getCircuitBreakerStatus();

            // then
            assertThat(status.state()).isEqualTo("OPEN");
        }
    }

    @Nested
    @DisplayName("Fallback 예외 처리 테스트")
    class FallbackExceptionHandlingTest {

        @Test
        @DisplayName("이벤트 직렬화 실패 시에도 예외가 전파되지 않는다")
        void shouldNotPropagateSerializationException() throws JsonProcessingException {
            // given
            ObjectMapper mockObjectMapper = mock(ObjectMapper.class);
            when(mockObjectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("직렬화 실패") {});

            ResilientExternalDataPlatformClient clientWithMockMapper = new ResilientExternalDataPlatformClient(
                    delegate,
                    circuitBreakerRegistry,
                    failedEventRepository,
                    mockObjectMapper
            );

            doThrow(new RuntimeException("전송 실패")).when(delegate).sendOrderData(any());

            // when & then - 예외가 발생하지 않음
            clientWithMockMapper.sendOrderData(testEvent);

            verify(failedEventRepository, never()).save(any());
        }

        @Test
        @DisplayName("실패 이벤트 저장 실패 시에도 예외가 전파되지 않는다")
        void shouldNotPropagateRepositorySaveException() {
            // given
            doThrow(new RuntimeException("전송 실패")).when(delegate).sendOrderData(any());
            doThrow(new RuntimeException("DB 저장 실패")).when(failedEventRepository).save(any());

            // when & then - 예외가 발생하지 않음
            resilientClient.sendOrderData(testEvent);

            verify(delegate).sendOrderData(testEvent);
            verify(failedEventRepository).save(any());
        }
    }
}
