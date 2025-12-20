package org.hhplus.hhecommerce.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueRequest;
import org.hhplus.hhecommerce.infrastructure.config.KafkaTopicProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "event.publisher.type", havingValue = "kafka", matchIfMissing = false)
public class CouponKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicProperties kafkaTopicProperties;

    private static final long SEND_TIMEOUT_SECONDS = 10;

    public String requestIssue(Long couponId, Long userId) {
        String requestId = UUID.randomUUID().toString();
        CouponIssueRequest request = CouponIssueRequest.of(requestId, couponId, userId);

        String topic = kafkaTopicProperties.getCouponIssueRequest();
        String key = String.valueOf(couponId);

        try {
            SendResult<String, Object> result = kafkaTemplate.send(topic, key, request)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.info("[Kafka] 쿠폰 발급 요청 발행 성공 - topic: {}, key: {}, partition: {}, offset: {}, requestId: {}",
                    topic, key,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset(),
                    requestId);

            return requestId;

        } catch (TimeoutException e) {
            log.error("[Kafka] 쿠폰 발급 요청 발행 타임아웃 - topic: {}, key: {}, requestId: {}", topic, key, requestId, e);
            throw new RuntimeException("쿠폰 발급 요청 전송 타임아웃", e);
        } catch (ExecutionException e) {
            log.error("[Kafka] 쿠폰 발급 요청 발행 실패 - topic: {}, key: {}, requestId: {}, error: {}",
                    topic, key, requestId, e.getCause().getMessage(), e);
            throw new RuntimeException("쿠폰 발급 요청 전송 실패", e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Kafka] 쿠폰 발급 요청 발행 중단 - topic: {}, key: {}, requestId: {}", topic, key, requestId, e);
            throw new RuntimeException("쿠폰 발급 요청 전송 중단", e);
        }
    }
}
