package org.hhplus.hhecommerce.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.order.OrderCompletedEvent;
import org.hhplus.hhecommerce.domain.order.PaymentCompletedEvent;
import org.hhplus.hhecommerce.infrastructure.config.KafkaTopicProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "event.publisher.type", havingValue = "kafka", matchIfMissing = false)
public class OrderKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties kafkaTopicProperties;

    public void sendOrderCompletedEvent(OrderCompletedEvent event) {
        String topic = kafkaTopicProperties.getOrderCompleted();
        String key = String.valueOf(event.orderId());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("[Kafka] 주문 완료 이벤트 발행 성공 - topic: {}, key: {}, partition: {}, offset: {}",
                        topic, key,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("[Kafka] 주문 완료 이벤트 발행 실패 - topic: {}, key: {}, error: {}",
                        topic, key, ex.getMessage(), ex);
            }
        });
    }

    public void sendPaymentCompletedEvent(PaymentCompletedEvent event) {
        String topic = kafkaTopicProperties.getPaymentCompleted();
        String key = String.valueOf(event.orderId());

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("[Kafka] 결제 완료 이벤트 발행 성공 - topic: {}, key: {}, partition: {}, offset: {}",
                        topic, key,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("[Kafka] 결제 완료 이벤트 발행 실패 - topic: {}, key: {}, error: {}",
                        topic, key, ex.getMessage(), ex);
            }
        });
    }
}
