package org.hhplus.hhecommerce.infrastructure.external;

import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.order.PaymentCompletedEvent;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Component
public class NotificationClient {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 HH:mm");
    private static final NumberFormat CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.KOREA);

    @Retryable(
        retryFor = ExternalApiException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 500, multiplier = 2, maxDelay = 2000)
    )
    public void sendOrderConfirmation(PaymentCompletedEvent event) {
        log.info("알림톡 발송 시작 - orderId: {}, phone: {}",
                event.orderId(), maskPhone(event.userPhone()));

        try {
            String message = buildOrderConfirmationMessage(event);

            simulateAlimtalkApiCall(event.userPhone(), message);

            log.info("알림톡 발송 완료 - orderId: {}", event.orderId());

        } catch (Exception e) {
            log.warn("알림톡 발송 실패 - orderId: {}, error: {}",
                    event.orderId(), e.getMessage());
            throw new ExternalApiException("알림톡 발송 실패", e);
        }
    }

    @Recover
    public void recoverWithSms(ExternalApiException e, PaymentCompletedEvent event) {
        log.warn("알림톡 발송 실패, SMS 대체 발송 시도 - orderId: {}", event.orderId());

        try {
            sendSmsNotification(event);
            log.info("SMS 대체 발송 완료 - orderId: {}", event.orderId());
        } catch (Exception smsError) {
            log.error("알림 발송 최종 실패 (알림톡/SMS 모두 실패) - orderId: {}, " +
                      "alimtalkError: {}, smsError: {}",
                    event.orderId(), e.getMessage(), smsError.getMessage());
        }
    }

    private void sendSmsNotification(PaymentCompletedEvent event) {
        String smsMessage = String.format("[주문확인] 주문번호 %d, 결제금액 %s",
                event.orderId(), CURRENCY_FORMAT.format(event.finalAmount()));

        log.debug("SMS 발송 시뮬레이션 - phone: {}, message: {}",
                maskPhone(event.userPhone()), smsMessage);
    }

    private String buildOrderConfirmationMessage(PaymentCompletedEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("[주문 확인 안내]\n\n");
        sb.append("주문번호: ").append(event.orderId()).append("\n");
        sb.append("주문일시: ").append(event.orderedAt().format(DATE_FORMATTER)).append("\n\n");

        sb.append("[주문 상품]\n");
        for (PaymentCompletedEvent.OrderItemInfo item : event.orderItems()) {
            sb.append("- ").append(item.productName());
            if (item.optionName() != null && !item.optionName().isEmpty()) {
                sb.append(" (").append(item.optionName()).append(")");
            }
            sb.append(" x ").append(item.quantity()).append("개\n");
        }

        sb.append("\n");
        sb.append("상품금액: ").append(CURRENCY_FORMAT.format(event.totalAmount())).append("\n");
        if (event.discountAmount() > 0) {
            sb.append("할인금액: -").append(CURRENCY_FORMAT.format(event.discountAmount())).append("\n");
        }
        sb.append("결제금액: ").append(CURRENCY_FORMAT.format(event.finalAmount())).append("\n\n");
        sb.append("감사합니다.");

        return sb.toString();
    }

    private void simulateAlimtalkApiCall(String phone, String message) {
        log.debug("알림톡 API 호출 시뮬레이션 - phone: {}, messageLength: {}",
                maskPhone(phone), message.length());
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "***";
        }
        return phone.substring(0, phone.length() - 4) + "****";
    }
}
