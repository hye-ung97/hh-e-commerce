package org.hhplus.hhecommerce.infrastructure.external;

import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.order.PaymentCompletedEvent;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExternalDataPlatformClient {

    @Retryable(
        retryFor = ExternalApiException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 4000)
    )
    public void sendOrderData(PaymentCompletedEvent event) {
        log.info("외부 데이터 플랫폼 전송 시작 - orderId: {}", event.orderId());

        try {
            simulateExternalApiCall(event);

            log.info("외부 데이터 플랫폼 전송 완료 - orderId: {}, finalAmount: {}",
                    event.orderId(), event.finalAmount());

        } catch (Exception e) {
            log.warn("외부 데이터 플랫폼 전송 실패 - orderId: {}, error: {}",
                    event.orderId(), e.getMessage());
            throw new ExternalApiException("데이터 플랫폼 전송 실패", e);
        }
    }

    @Recover
    public void recoverFromApiFailure(ExternalApiException e, PaymentCompletedEvent event) {
        log.error("외부 데이터 플랫폼 전송 최종 실패 - orderId: {}, 재시도 횟수 초과. " +
                  "배치 재처리 대상으로 기록합니다.", event.orderId(), e);

        recordFailedEvent(event, e);
    }

    private void simulateExternalApiCall(PaymentCompletedEvent event) {
        log.debug("외부 API 호출 시뮬레이션 - orderId: {}, items: {}",
                event.orderId(), event.orderItems().size());
    }

    private void recordFailedEvent(PaymentCompletedEvent event, Exception e) {
        log.warn("실패 이벤트 기록 - orderId: {}, type: DATA_PLATFORM, error: {}",
                event.orderId(), e.getMessage());
    }
}
