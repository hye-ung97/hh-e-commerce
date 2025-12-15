package org.hhplus.hhecommerce.infrastructure.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.hhplus.hhecommerce.domain.order.OrderCompletedEvent;
import org.hhplus.hhecommerce.domain.order.PaymentCompletedEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@DisplayName("Kafka 메시지 발행/수신 통합 테스트 (Testcontainers)")
class KafkaIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(KafkaIntegrationTest.class);

    private static final String ORDER_COMPLETED_TOPIC = "order-completed";
    private static final String PAYMENT_COMPLETED_TOPIC = "payment-completed";

    // 테스트 격리를 위한 고유 토픽 (테스트 간 메시지 간섭 방지)
    private static final String ORDER_COMPLETED_TOPIC_TEST1 = "order-completed-test1";
    private static final String ORDER_COMPLETED_TOPIC_TEST3 = "order-completed-test3";

    @Container
    static final KafkaContainer KAFKA_CONTAINER = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    ).withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static final MySQLContainer<?> MYSQL_CONTAINER = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("ecommerce")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Kafka 설정
        registry.add("spring.kafka.bootstrap-servers", KAFKA_CONTAINER::getBootstrapServers);
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
        registry.add("spring.kafka.consumer.group-id", () -> "test-group");
        registry.add("event.publisher.type", () -> "kafka");

        // Kafka Topic 설정
        registry.add("kafka.topic.order-completed", () -> ORDER_COMPLETED_TOPIC);
        registry.add("kafka.topic.payment-completed", () -> PAYMENT_COMPLETED_TOPIC);
        registry.add("kafka.topic.coupon-issue-request", () -> "coupon-issue-request");
        registry.add("kafka.topic.partitions", () -> "1");
        registry.add("kafka.topic.replicas", () -> "1");

        // MySQL 설정
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        // Redis 비활성화 (테스트에서는 불필요)
        registry.add("spring.cache.type", () -> "none");
        registry.add("spring.autoconfigure.exclude", () ->
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration"
        );
    }

    @BeforeAll
    static void setUp() {
        log.info("========================================");
        log.info("[Testcontainers] Kafka 컨테이너 정보");
        log.info("[Testcontainers] Bootstrap Servers: {}", KAFKA_CONTAINER.getBootstrapServers());
        log.info("[Testcontainers] Container ID: {}", KAFKA_CONTAINER.getContainerId());
        log.info("========================================");
    }

    @Test
    @DisplayName("주문 완료 이벤트 발행 및 수신 테스트")
    void testOrderCompletedEvent_PublishAndConsume() throws Exception {
        log.info("========================================");
        log.info("[테스트 시작] 주문 완료 이벤트 발행/수신");
        log.info("[Kafka] Bootstrap Servers: {}", KAFKA_CONTAINER.getBootstrapServers());
        log.info("========================================");

        // Given - Producer 설정
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");

        DefaultKafkaProducerFactory<String, OrderCompletedEvent> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        KafkaTemplate<String, OrderCompletedEvent> kafkaTemplate = new KafkaTemplate<>(producerFactory);

        // Consumer 설정
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-order-group");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        JsonDeserializer<OrderCompletedEvent> deserializer = new JsonDeserializer<>(OrderCompletedEvent.class);
        deserializer.addTrustedPackages("*");

        DefaultKafkaConsumerFactory<String, OrderCompletedEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), deserializer);

        // 메시지 수신을 위한 큐
        BlockingQueue<ConsumerRecord<String, OrderCompletedEvent>> records = new LinkedBlockingQueue<>();

        ContainerProperties containerProperties = new ContainerProperties(ORDER_COMPLETED_TOPIC_TEST1);
        containerProperties.setMessageListener((MessageListener<String, OrderCompletedEvent>) record -> {
            log.info("========================================");
            log.info("[Kafka Consumer] 메시지 수신 성공!");
            log.info("[Kafka Consumer] Topic: {}", record.topic());
            log.info("[Kafka Consumer] Partition: {}", record.partition());
            log.info("[Kafka Consumer] Offset: {}", record.offset());
            log.info("[Kafka Consumer] Key: {}", record.key());
            log.info("[Kafka Consumer] OrderId: {}", record.value().orderId());
            log.info("[Kafka Consumer] Products: {}", record.value().productQuantityMap());
            log.info("========================================");
            records.add(record);
        });

        KafkaMessageListenerContainer<String, OrderCompletedEvent> container =
                new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        container.start();

        // Consumer가 파티션에 할당될 때까지 대기
        Thread.sleep(2000);

        // When - 메시지 발행
        OrderCompletedEvent event = new OrderCompletedEvent(
                1001L,  // orderId
                Map.of(101L, 2, 102L, 1)  // productQuantityMap: 상품101 2개, 상품102 1개
        );

        log.info("========================================");
        log.info("[Kafka Producer] 메시지 발행 시작");
        log.info("[Kafka Producer] Topic: {}", ORDER_COMPLETED_TOPIC_TEST1);
        log.info("[Kafka Producer] Key: {}", event.orderId());
        log.info("[Kafka Producer] OrderId: {}", event.orderId());
        log.info("[Kafka Producer] Products: {}", event.productQuantityMap());
        log.info("========================================");

        CompletableFuture<SendResult<String, OrderCompletedEvent>> future =
                kafkaTemplate.send(ORDER_COMPLETED_TOPIC_TEST1, String.valueOf(event.orderId()), event);

        SendResult<String, OrderCompletedEvent> sendResult = future.get(10, TimeUnit.SECONDS);

        log.info("========================================");
        log.info("[Kafka Producer] 발행 완료!");
        log.info("[Kafka Producer] Topic: {}", sendResult.getRecordMetadata().topic());
        log.info("[Kafka Producer] Partition: {}", sendResult.getRecordMetadata().partition());
        log.info("[Kafka Producer] Offset: {}", sendResult.getRecordMetadata().offset());
        log.info("[Kafka Producer] Timestamp: {}", sendResult.getRecordMetadata().timestamp());
        log.info("========================================");

        // Then - 메시지 수신 확인
        ConsumerRecord<String, OrderCompletedEvent> received = records.poll(10, TimeUnit.SECONDS);

        assertThat(received).isNotNull();
        assertThat(received.key()).isEqualTo("1001");
        assertThat(received.value().orderId()).isEqualTo(1001L);
        assertThat(received.value().productQuantityMap()).hasSize(2);
        assertThat(received.value().productQuantityMap().get(101L)).isEqualTo(2);

        log.info("========================================");
        log.info("[테스트 완료] 주문 완료 이벤트 정상 발행/수신 확인!");
        log.info("[검증] Key: {} ✓", received.key());
        log.info("[검증] OrderId: {} ✓", received.value().orderId());
        log.info("[검증] Products: {} ✓", received.value().productQuantityMap());
        log.info("========================================");

        container.stop();
    }

    @Test
    @DisplayName("결제 완료 이벤트 발행 및 수신 테스트")
    void testPaymentCompletedEvent_PublishAndConsume() throws Exception {
        log.info("========================================");
        log.info("[테스트 시작] 결제 완료 이벤트 발행/수신");
        log.info("========================================");

        // Producer 설정
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");

        DefaultKafkaProducerFactory<String, PaymentCompletedEvent> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        KafkaTemplate<String, PaymentCompletedEvent> kafkaTemplate = new KafkaTemplate<>(producerFactory);

        // Consumer 설정
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-payment-group");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<PaymentCompletedEvent> deserializer = new JsonDeserializer<>(PaymentCompletedEvent.class);
        deserializer.addTrustedPackages("*");

        DefaultKafkaConsumerFactory<String, PaymentCompletedEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), deserializer);

        BlockingQueue<ConsumerRecord<String, PaymentCompletedEvent>> records = new LinkedBlockingQueue<>();

        ContainerProperties containerProperties = new ContainerProperties(PAYMENT_COMPLETED_TOPIC);
        containerProperties.setMessageListener((MessageListener<String, PaymentCompletedEvent>) record -> {
            log.info("========================================");
            log.info("[Kafka Consumer] 결제 완료 이벤트 수신!");
            log.info("[Kafka Consumer] Topic: {}", record.topic());
            log.info("[Kafka Consumer] Partition: {}", record.partition());
            log.info("[Kafka Consumer] Offset: {}", record.offset());
            log.info("[Kafka Consumer] Key: {}", record.key());
            log.info("[Kafka Consumer] Version: {}", record.value().version());
            log.info("[Kafka Consumer] OrderId: {}", record.value().orderId());
            log.info("[Kafka Consumer] UserId: {}", record.value().userId());
            log.info("[Kafka Consumer] TotalAmount: {}원", record.value().totalAmount());
            log.info("[Kafka Consumer] DiscountAmount: {}원", record.value().discountAmount());
            log.info("[Kafka Consumer] FinalAmount: {}원", record.value().finalAmount());
            log.info("[Kafka Consumer] UserPhone: {}", record.value().userPhone());
            log.info("[Kafka Consumer] OrderItems: {}", record.value().orderItems());
            log.info("========================================");
            records.add(record);
        });

        KafkaMessageListenerContainer<String, PaymentCompletedEvent> container =
                new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        container.start();
        Thread.sleep(2000);

        // When - 결제 완료 이벤트 발행 (static factory method 사용)
        List<PaymentCompletedEvent.OrderItemInfo> orderItems = List.of(
                new PaymentCompletedEvent.OrderItemInfo(101L, "노트북", "실버", 1, 50000, 50000)
        );

        PaymentCompletedEvent event = PaymentCompletedEvent.of(
                2001L,              // orderId
                1L,                 // userId
                "010-1234-5678",    // userPhone
                50000,              // totalAmount (5만원)
                5000,               // discountAmount (5천원 할인)
                45000,              // finalAmount (4만5천원 결제)
                orderItems,         // orderItems
                Map.of(101L, 1),    // productQuantityMap
                LocalDateTime.now() // orderedAt
        );

        log.info("========================================");
        log.info("[Kafka Producer] 결제 완료 이벤트 발행");
        log.info("[Kafka Producer] Topic: {}", PAYMENT_COMPLETED_TOPIC);
        log.info("[Kafka Producer] Version: {}", event.version());
        log.info("[Kafka Producer] OrderId: {}", event.orderId());
        log.info("[Kafka Producer] UserId: {}", event.userId());
        log.info("[Kafka Producer] TotalAmount: {}원", event.totalAmount());
        log.info("[Kafka Producer] DiscountAmount: {}원", event.discountAmount());
        log.info("[Kafka Producer] FinalAmount: {}원", event.finalAmount());
        log.info("[Kafka Producer] OrderItems: {}", event.orderItems());
        log.info("========================================");

        SendResult<String, PaymentCompletedEvent> sendResult =
                kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC, String.valueOf(event.orderId()), event)
                        .get(10, TimeUnit.SECONDS);

        log.info("[Kafka Producer] 발행 완료 - partition: {}, offset: {}",
                sendResult.getRecordMetadata().partition(),
                sendResult.getRecordMetadata().offset());

        // Then
        ConsumerRecord<String, PaymentCompletedEvent> received = records.poll(10, TimeUnit.SECONDS);

        assertThat(received).isNotNull();
        assertThat(received.value().version()).isEqualTo(PaymentCompletedEvent.CURRENT_VERSION);
        assertThat(received.value().orderId()).isEqualTo(2001L);
        assertThat(received.value().userId()).isEqualTo(1L);
        assertThat(received.value().totalAmount()).isEqualTo(50000);
        assertThat(received.value().discountAmount()).isEqualTo(5000);
        assertThat(received.value().finalAmount()).isEqualTo(45000);
        assertThat(received.value().userPhone()).isEqualTo("010-1234-5678");
        assertThat(received.value().orderItems()).hasSize(1);

        log.info("========================================");
        log.info("[테스트 완료] 결제 완료 이벤트 정상 발행/수신!");
        log.info("[검증] Version: {} ✓", received.value().version());
        log.info("[검증] OrderId: {} ✓", received.value().orderId());
        log.info("[검증] FinalAmount: {}원 ✓", received.value().finalAmount());
        log.info("========================================");

        container.stop();
    }

    @Test
    @DisplayName("여러 주문 이벤트 순차 발행 및 수신 테스트")
    void testMultipleOrderEvents_PublishAndConsumeInOrder() throws Exception {
        log.info("========================================");
        log.info("[테스트 시작] 여러 주문 이벤트 순차 발행/수신");
        log.info("========================================");

        // Producer 설정
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        DefaultKafkaProducerFactory<String, OrderCompletedEvent> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        KafkaTemplate<String, OrderCompletedEvent> kafkaTemplate = new KafkaTemplate<>(producerFactory);

        // Consumer 설정
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "test-multi-order-group");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<OrderCompletedEvent> deserializer = new JsonDeserializer<>(OrderCompletedEvent.class);
        deserializer.addTrustedPackages("*");

        DefaultKafkaConsumerFactory<String, OrderCompletedEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), deserializer);

        BlockingQueue<ConsumerRecord<String, OrderCompletedEvent>> records = new LinkedBlockingQueue<>();

        ContainerProperties containerProperties = new ContainerProperties(ORDER_COMPLETED_TOPIC_TEST3);
        containerProperties.setMessageListener((MessageListener<String, OrderCompletedEvent>) record -> {
            log.info("[Consumer] 수신 - orderId: {}, partition: {}, offset: {}",
                    record.value().orderId(), record.partition(), record.offset());
            records.add(record);
        });

        KafkaMessageListenerContainer<String, OrderCompletedEvent> container =
                new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        container.start();
        Thread.sleep(2000);

        // When - 5개 주문 이벤트 발행
        int messageCount = 5;
        log.info("[Producer] {}개 메시지 발행 시작", messageCount);

        for (int i = 1; i <= messageCount; i++) {
            OrderCompletedEvent event = new OrderCompletedEvent(
                    (long) (3000 + i),  // orderId: 3001, 3002, 3003, 3004, 3005
                    Map.of((long) (100 + i), i)  // 상품
            );

            SendResult<String, OrderCompletedEvent> result =
                    kafkaTemplate.send(ORDER_COMPLETED_TOPIC_TEST3, String.valueOf(event.orderId()), event)
                            .get(10, TimeUnit.SECONDS);

            log.info("[Producer] 발행 #{} - orderId: {}, partition: {}, offset: {}",
                    i, event.orderId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        }

        log.info("[Producer] {}개 메시지 발행 완료!", messageCount);

        // Then - 수신 확인
        log.info("[Consumer] 메시지 수신 대기...");

        int receivedCount = 0;
        while (receivedCount < messageCount) {
            ConsumerRecord<String, OrderCompletedEvent> received = records.poll(10, TimeUnit.SECONDS);
            if (received != null) {
                receivedCount++;
                log.info("[Consumer] 수신 #{} - orderId: {}",
                        receivedCount, received.value().orderId());
                assertThat(received.value().orderId()).isGreaterThanOrEqualTo(3001L);
            }
        }

        assertThat(receivedCount).isEqualTo(messageCount);

        log.info("========================================");
        log.info("[테스트 완료] {}개 메시지 모두 정상 수신!", messageCount);
        log.info("========================================");

        container.stop();
    }
}
