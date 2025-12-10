package org.hhplus.hhecommerce.application.order;

import org.hhplus.hhecommerce.domain.order.PaymentCompletedEvent;
import org.hhplus.hhecommerce.infrastructure.external.ExternalApiException;
import org.hhplus.hhecommerce.infrastructure.external.ResilientExternalDataPlatformClient;
import org.hhplus.hhecommerce.infrastructure.external.ResilientNotificationClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventListener 테스트")
class PaymentEventListenerTest {

    @Mock
    private ResilientExternalDataPlatformClient externalDataPlatformClient;

    @Mock
    private ResilientNotificationClient notificationClient;

    @InjectMocks
    private PaymentEventListener paymentEventListener;

    private PaymentCompletedEvent testEvent;

    @BeforeEach
    void setUp() {
        List<PaymentCompletedEvent.OrderItemInfo> orderItems = List.of(
                new PaymentCompletedEvent.OrderItemInfo(1L, "테스트 상품", "옵션A", 2, 10000, 20000)
        );

        testEvent = PaymentCompletedEvent.of(
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
    @DisplayName("데이터 플랫폼 전송 테스트")
    class DataPlatformTransferTest {

        @Test
        @DisplayName("결제 완료 시 외부 데이터 플랫폼에 주문 정보가 전송된다")
        void shouldSendOrderDataToExternalPlatform() {
            // when
            paymentEventListener.handleDataPlatformTransfer(testEvent);

            // then
            verify(externalDataPlatformClient, times(1)).sendOrderData(testEvent);
        }

        @Test
        @DisplayName("데이터 플랫폼 전송 실패 시 예외가 전파되지 않는다")
        void shouldNotPropagateExceptionWhenDataPlatformFails() {
            // given
            doThrow(new ExternalApiException("전송 실패"))
                    .when(externalDataPlatformClient).sendOrderData(any());

            // when & then - 예외가 발생하지 않음
            paymentEventListener.handleDataPlatformTransfer(testEvent);

            verify(externalDataPlatformClient, times(1)).sendOrderData(testEvent);
        }
    }

    @Nested
    @DisplayName("알림톡 발송 테스트")
    class NotificationTest {

        @Test
        @DisplayName("결제 완료 시 사용자에게 알림톡이 발송된다")
        void shouldSendNotificationToUser() {
            // when
            paymentEventListener.handleNotification(testEvent);

            // then
            verify(notificationClient, times(1)).sendOrderConfirmation(testEvent);
        }

        @Test
        @DisplayName("전화번호가 없으면 알림톡 발송을 생략한다")
        void shouldSkipNotificationWhenPhoneIsNull() {
            // given
            PaymentCompletedEvent eventWithoutPhone = PaymentCompletedEvent.of(
                    1L, 100L, null,  // 전화번호 없음
                    20000, 2000, 18000,
                    testEvent.orderItems(),
                    testEvent.productQuantityMap(),
                    LocalDateTime.now()
            );

            // when
            paymentEventListener.handleNotification(eventWithoutPhone);

            // then
            verify(notificationClient, never()).sendOrderConfirmation(any());
        }

        @Test
        @DisplayName("전화번호가 빈 문자열이면 알림톡 발송을 생략한다")
        void shouldSkipNotificationWhenPhoneIsBlank() {
            // given
            PaymentCompletedEvent eventWithBlankPhone = PaymentCompletedEvent.of(
                    1L, 100L, "   ",  // 빈 전화번호
                    20000, 2000, 18000,
                    testEvent.orderItems(),
                    testEvent.productQuantityMap(),
                    LocalDateTime.now()
            );

            // when
            paymentEventListener.handleNotification(eventWithBlankPhone);

            // then
            verify(notificationClient, never()).sendOrderConfirmation(any());
        }

        @Test
        @DisplayName("알림톡 발송 실패 시 예외가 전파되지 않는다")
        void shouldNotPropagateExceptionWhenNotificationFails() {
            // given
            doThrow(new ExternalApiException("알림 발송 실패"))
                    .when(notificationClient).sendOrderConfirmation(any());

            // when & then - 예외가 발생하지 않음
            paymentEventListener.handleNotification(testEvent);

            verify(notificationClient, times(1)).sendOrderConfirmation(testEvent);
        }
    }

    @Nested
    @DisplayName("독립적 이벤트 소비자 테스트")
    class IndependentConsumerTest {

        @Test
        @DisplayName("데이터 플랫폼 전송 실패가 알림톡 발송에 영향을 주지 않는다")
        void dataPlatformFailureShouldNotAffectNotification() {
            // given
            doThrow(new ExternalApiException("전송 실패"))
                    .when(externalDataPlatformClient).sendOrderData(any());

            // when
            paymentEventListener.handleDataPlatformTransfer(testEvent);
            paymentEventListener.handleNotification(testEvent);

            // then - 알림톡은 정상적으로 호출됨
            verify(notificationClient, times(1)).sendOrderConfirmation(testEvent);
        }

        @Test
        @DisplayName("알림톡 발송 실패가 데이터 플랫폼 전송에 영향을 주지 않는다")
        void notificationFailureShouldNotAffectDataPlatform() {
            // given
            doThrow(new ExternalApiException("알림 발송 실패"))
                    .when(notificationClient).sendOrderConfirmation(any());

            // when
            paymentEventListener.handleNotification(testEvent);
            paymentEventListener.handleDataPlatformTransfer(testEvent);

            // then - 데이터 플랫폼 전송은 정상적으로 호출됨
            verify(externalDataPlatformClient, times(1)).sendOrderData(testEvent);
        }
    }
}
