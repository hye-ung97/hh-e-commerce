package org.hhplus.hhecommerce.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.common.ProcessedEvent;
import org.hhplus.hhecommerce.domain.common.ProcessedEventRepository;
import org.hhplus.hhecommerce.domain.order.OrderCompletedEvent;
import org.hhplus.hhecommerce.domain.order.PaymentCompletedEvent;
import org.hhplus.hhecommerce.infrastructure.external.ResilientExternalDataPlatformClient;
import org.hhplus.hhecommerce.infrastructure.external.ResilientNotificationClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "event.publisher.type", havingValue = "kafka", matchIfMissing = false)
public class OrderKafkaConsumer {

    private final ResilientExternalDataPlatformClient dataPlatformClient;
    private final ResilientNotificationClient notificationClient;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(
            topics = "${kafka.topic.order-completed}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumeOrderCompletedEvent(
            @Payload OrderCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key
    ) {
        log.info("[Kafka Consumer] 주문 완료 이벤트 수신 - topic: {}, partition: {}, offset: {}, key: {}",
                topic, partition, offset, key);

        String eventId = ProcessedEvent.generateEventId("Order", event.orderId(), "OrderCompletedEvent");

        if (isAlreadyProcessed(eventId)) {
            log.info("[Kafka Consumer] 이미 처리된 이벤트 무시 - eventId: {}", eventId);
            return;
        }

        log.info("[Kafka Consumer] 주문 정보 - orderId: {}, products: {}",
                event.orderId(), event.productQuantityMap());

        processOrderCompletedEvent(event);
        markAsProcessed(eventId, "OrderCompletedEvent");
    }

    @KafkaListener(
            topics = "${kafka.topic.payment-completed}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consumePaymentCompletedEvent(
            @Payload PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key
    ) {
        log.info("[Kafka Consumer] 결제 완료 이벤트 수신 - topic: {}, partition: {}, offset: {}, key: {}",
                topic, partition, offset, key);

        String eventId = ProcessedEvent.generateEventId("Payment", event.orderId(), "PaymentCompletedEvent");

        if (isAlreadyProcessed(eventId)) {
            log.info("[Kafka Consumer] 이미 처리된 이벤트 무시 - eventId: {}", eventId);
            return;
        }

        log.info("[Kafka Consumer] 결제 정보 - orderId: {}, userId: {}, finalAmount: {}",
                event.orderId(), event.userId(), event.finalAmount());

        processPaymentCompletedEvent(event);
        markAsProcessed(eventId, "PaymentCompletedEvent");
    }

    private void processOrderCompletedEvent(OrderCompletedEvent event) {
        log.debug("[Data Platform] 주문 데이터 전송 시작 - orderId: {}", event.orderId());
        log.info("[Data Platform] 주문 데이터 전송 완료 - orderId: {}", event.orderId());
    }

    private void processPaymentCompletedEvent(PaymentCompletedEvent event) {
        log.debug("[Kafka Consumer] 결제 이벤트 처리 시작 - orderId: {}", event.orderId());
        sendToDataPlatform(event);
        sendNotification(event);
    }

    private void sendToDataPlatform(PaymentCompletedEvent event) {
        try {
            dataPlatformClient.sendOrderData(event);
            log.info("[Data Platform] 데이터 전송 완료 - orderId: {}", event.orderId());
        } catch (Exception e) {
            log.error("[Data Platform] 데이터 전송 실패 - orderId: {}, error: {}",
                    event.orderId(), e.getMessage());
        }
    }

    private void sendNotification(PaymentCompletedEvent event) {
        if (event.userPhone() == null || event.userPhone().isBlank()) {
            log.info("[Notification] 알림 발송 생략 - orderId: {}, 전화번호 없음", event.orderId());
            return;
        }

        try {
            notificationClient.sendOrderConfirmation(event);
            log.info("[Notification] 알림 발송 완료 - orderId: {}, userId: {}",
                    event.orderId(), event.userId());
        } catch (Exception e) {
            log.error("[Notification] 알림 발송 실패 - orderId: {}, error: {}",
                    event.orderId(), e.getMessage());
        }
    }

    private boolean isAlreadyProcessed(String eventId) {
        return processedEventRepository.existsByEventId(eventId);
    }

    private void markAsProcessed(String eventId, String eventType) {
        processedEventRepository.save(new ProcessedEvent(eventId, eventType));
    }
}
