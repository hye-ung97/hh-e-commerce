package org.hhplus.hhecommerce.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "kafka.topic")
@ConditionalOnProperty(name = "event.publisher.type", havingValue = "kafka", matchIfMissing = false)
public class KafkaTopicProperties {

    private String orderCompleted = "order-completed";
    private String paymentCompleted = "payment-completed";
    private String couponIssueRequest = "coupon-issue-request";

    private int partitions = 3;
    private int replicas = 1;
}
