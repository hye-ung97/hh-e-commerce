package org.hhplus.hhecommerce.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.common.DomainEventPublisher;
import org.hhplus.hhecommerce.domain.order.OrderCompletedEvent;
import org.hhplus.hhecommerce.domain.order.PaymentCompletedEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "event.publisher.type", havingValue = "kafka", matchIfMissing = false)
@RequiredArgsConstructor
public class KafkaDomainEventPublisher implements DomainEventPublisher {

    private final OrderKafkaProducer orderKafkaProducer;

    @Override
    public void publish(Object event) {
        if (event == null) {
            log.warn("null 이벤트는 발행할 수 없습니다.");
            return;
        }

        if (event instanceof OrderCompletedEvent e) {
            orderKafkaProducer.sendOrderCompletedEvent(e);
            log.debug("[Kafka] OrderCompletedEvent 발행 - orderId: {}", e.orderId());
        } else if (event instanceof PaymentCompletedEvent e) {
            orderKafkaProducer.sendPaymentCompletedEvent(e);
            log.debug("[Kafka] PaymentCompletedEvent 발행 - orderId: {}", e.orderId());
        } else {
            log.warn("알 수 없는 이벤트 타입: {}", event.getClass().getSimpleName());
        }
    }
}
