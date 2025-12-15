package org.hhplus.hhecommerce.infrastructure.kafka;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.hhplus.hhecommerce.domain.coupon.CouponIssueRequest;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DisplayName("선착순 쿠폰 발급 Kafka 통합 테스트")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CouponKafkaIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(CouponKafkaIntegrationTest.class);

    private static final String COUPON_ISSUE_TOPIC = "coupon-issue-request-test";
    private static final int PARTITION_COUNT = 3;

    @Container
    static final KafkaContainer KAFKA_CONTAINER = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0")
    ).withStartupTimeout(Duration.ofMinutes(2));

    private KafkaTemplate<String, CouponIssueRequest> kafkaTemplate;
    private DefaultKafkaConsumerFactory<String, CouponIssueRequest> consumerFactory;

    @BeforeAll
    static void setUpTopics() throws Exception {
        log.info("========================================");
        log.info("[Setup] Kafka 컨테이너 시작");
        log.info("[Setup] Bootstrap Servers: {}", KAFKA_CONTAINER.getBootstrapServers());
        log.info("========================================");

        // 토픽 생성 (3개 파티션)
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA_CONTAINER.getBootstrapServers());

        try (AdminClient adminClient = AdminClient.create(props)) {
            NewTopic topic = new NewTopic(COUPON_ISSUE_TOPIC, PARTITION_COUNT, (short) 1);
            adminClient.createTopics(Collections.singleton(topic)).all().get(30, TimeUnit.SECONDS);
            log.info("[Setup] 토픽 생성 완료 - topic: {}, partitions: {}", COUPON_ISSUE_TOPIC, PARTITION_COUNT);
        }
    }

    @BeforeEach
    void setUp() {
        // Producer 설정
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");

        DefaultKafkaProducerFactory<String, CouponIssueRequest> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(producerFactory);

        // Consumer 설정
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        JsonDeserializer<CouponIssueRequest> deserializer = new JsonDeserializer<>(CouponIssueRequest.class);
        deserializer.addTrustedPackages("*");

        consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), deserializer);
    }

    // ==================== 테스트 1: 파티셔닝 전략 ====================

    @Test
    @Order(1)
    @DisplayName("테스트 1: 동일 couponId는 동일 파티션으로 라우팅")
    void test1_sameCouponIdGoesToSamePartition() throws Exception {
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════════╗");
        log.info("║  테스트 1: 파티셔닝 전략 - 동일 couponId → 동일 파티션            ║");
        log.info("╚══════════════════════════════════════════════════════════════════╝");

        Long couponId = 1L;
        int messageCount = 10;
        Set<Integer> partitions = ConcurrentHashMap.newKeySet();

        log.info("[시나리오] couponId={} 로 {}건의 요청 발행", couponId, messageCount);
        log.info("[기대결과] 모든 메시지가 동일한 파티션으로 라우팅");
        log.info("");

        // 동일 couponId로 10개 메시지 발행
        // 핵심: 선착순 보장을 위해 키는 couponId만 사용해야 함
        for (int i = 1; i <= messageCount; i++) {
            Long userId = (long) i;
            String key = String.valueOf(couponId);  // couponId만 키로 사용 → 동일 파티션 보장
            CouponIssueRequest request = CouponIssueRequest.of(
                    UUID.randomUUID().toString(),
                    couponId,
                    userId
            );

            SendResult<String, CouponIssueRequest> result =
                    kafkaTemplate.send(COUPON_ISSUE_TOPIC, key, request).get(10, TimeUnit.SECONDS);

            int partition = result.getRecordMetadata().partition();
            partitions.add(partition);

            log.info("[Producer] 발행 #{} - key: {}, partition: {}, offset: {}, userId: {}",
                    i, key, partition, result.getRecordMetadata().offset(), userId);
        }

        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────────┐");
        log.info("│ 결과: 사용된 파티션 = {} (총 {}개)                              ", partitions, partitions.size());
        log.info("└─────────────────────────────────────────────────────────────────┘");

        // 검증: 모든 메시지가 동일 파티션으로
        assertThat(partitions).hasSize(1);
        log.info("[검증 통과] ✅ 동일 couponId의 모든 메시지가 파티션 {}로 라우팅됨", partitions.iterator().next());
        log.info("");
    }

    @Test
    @Order(2)
    @DisplayName("테스트 2: 다른 couponId는 다른 파티션으로 분산")
    void test2_differentCouponIdDistributedToPartitions() throws Exception {
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════════╗");
        log.info("║  테스트 2: 파티셔닝 전략 - 다른 couponId → 파티션 분산            ║");
        log.info("╚══════════════════════════════════════════════════════════════════╝");

        int couponCount = 9;  // 3개 파티션에 균등 분산 기대
        Map<Long, Integer> couponPartitionMap = new HashMap<>();

        log.info("[시나리오] {} 종류의 쿠폰에 대해 각각 발급 요청", couponCount);
        log.info("[기대결과] 메시지가 여러 파티션에 분산");
        log.info("");

        for (long couponId = 1; couponId <= couponCount; couponId++) {
            String key = couponId + ":1";
            CouponIssueRequest request = CouponIssueRequest.of(
                    UUID.randomUUID().toString(),
                    couponId,
                    1L
            );

            SendResult<String, CouponIssueRequest> result =
                    kafkaTemplate.send(COUPON_ISSUE_TOPIC, key, request).get(10, TimeUnit.SECONDS);

            int partition = result.getRecordMetadata().partition();
            couponPartitionMap.put(couponId, partition);

            log.info("[Producer] couponId: {} → partition: {}", couponId, partition);
        }

        // 파티션별 쿠폰 그룹핑
        Map<Integer, List<Long>> partitionCoupons = couponPartitionMap.entrySet().stream()
                .collect(Collectors.groupingBy(
                        Map.Entry::getValue,
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())
                ));

        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────────┐");
        log.info("│ 파티션별 쿠폰 분포:                                              │");
        partitionCoupons.forEach((partition, coupons) ->
                log.info("│   Partition {} : couponIds = {}                        ", partition, coupons));
        log.info("│ 사용된 파티션 수: {}                                            ", partitionCoupons.size());
        log.info("└─────────────────────────────────────────────────────────────────┘");

        // 검증: 여러 파티션에 분산
        assertThat(partitionCoupons.size()).isGreaterThan(1);
        log.info("[검증 통과] ✅ 메시지가 {}개 파티션에 분산됨", partitionCoupons.size());
        log.info("");
    }

    // ==================== 테스트 3: Consumer 병렬 확장 ====================

    @Test
    @Order(3)
    @DisplayName("테스트 3: Consumer 병렬 처리 - 다중 파티션 동시 소비")
    void test3_parallelConsumerProcessing() throws Exception {
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════════╗");
        log.info("║  테스트 3: Consumer 병렬 확장 - 다중 파티션 동시 소비             ║");
        log.info("╚══════════════════════════════════════════════════════════════════╝");

        String uniqueTopic = COUPON_ISSUE_TOPIC + "-parallel-" + System.currentTimeMillis();

        // 새 토픽 생성
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA_CONTAINER.getBootstrapServers());
        try (AdminClient adminClient = AdminClient.create(props)) {
            NewTopic topic = new NewTopic(uniqueTopic, PARTITION_COUNT, (short) 1);
            adminClient.createTopics(Collections.singleton(topic)).all().get(30, TimeUnit.SECONDS);
        }

        int messageCount = 30;
        Map<Integer, AtomicInteger> partitionProcessCount = new ConcurrentHashMap<>();
        Map<Integer, List<String>> partitionThreads = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(messageCount);

        for (int i = 0; i < PARTITION_COUNT; i++) {
            partitionProcessCount.put(i, new AtomicInteger(0));
            partitionThreads.put(i, Collections.synchronizedList(new ArrayList<>()));
        }

        log.info("[시나리오] {}개 메시지를 발행하고 3개 Consumer가 병렬 처리", messageCount);
        log.info("[설정] 토픽 파티션: {}, Consumer 병렬도: {}", PARTITION_COUNT, PARTITION_COUNT);
        log.info("");

        // Consumer 설정 (3개 병렬)
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "parallel-test-group-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<CouponIssueRequest> deserializer = new JsonDeserializer<>(CouponIssueRequest.class);
        deserializer.addTrustedPackages("*");

        DefaultKafkaConsumerFactory<String, CouponIssueRequest> testConsumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), deserializer);

        ContainerProperties containerProperties = new ContainerProperties(uniqueTopic);
        containerProperties.setMessageListener((MessageListener<String, CouponIssueRequest>) record -> {
            int partition = record.partition();
            partitionProcessCount.get(partition).incrementAndGet();
            partitionThreads.get(partition).add(Thread.currentThread().getName());

            log.info("[Consumer] partition: {}, offset: {}, thread: {}, couponId: {}, userId: {}",
                    partition, record.offset(), Thread.currentThread().getName(),
                    record.value().couponId(), record.value().userId());

            latch.countDown();
        });

        ConcurrentMessageListenerContainer<String, CouponIssueRequest> container =
                new ConcurrentMessageListenerContainer<>(testConsumerFactory, containerProperties);
        container.setConcurrency(PARTITION_COUNT);  // 병렬도 설정
        container.start();

        Thread.sleep(3000);  // Consumer 준비 대기

        // 메시지 발행 (여러 couponId로 분산)
        log.info("[Producer] {}개 메시지 발행 시작...", messageCount);
        for (int i = 0; i < messageCount; i++) {
            long couponId = (i % PARTITION_COUNT) + 1;  // 1, 2, 3 순환
            String key = couponId + ":" + i;
            CouponIssueRequest request = CouponIssueRequest.of(
                    UUID.randomUUID().toString(),
                    couponId,
                    (long) i
            );
            kafkaTemplate.send(uniqueTopic, key, request);
        }
        log.info("[Producer] 발행 완료");

        // 수신 대기
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        container.stop();

        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────────┐");
        log.info("│ 파티션별 처리 현황:                                              │");
        partitionProcessCount.forEach((partition, count) -> {
            Set<String> uniqueThreads = new HashSet<>(partitionThreads.get(partition));
            log.info("│   Partition {} : {}건 처리, 스레드: {}                ", partition, count.get(), uniqueThreads);
        });
        log.info("│ 총 처리: {}건 / {}건                                        ",
                messageCount - (int) latch.getCount(), messageCount);
        log.info("└─────────────────────────────────────────────────────────────────┘");

        assertThat(completed).isTrue();
        assertThat(partitionProcessCount.values().stream().mapToInt(AtomicInteger::get).sum()).isEqualTo(messageCount);
        log.info("[검증 통과] ✅ 모든 메시지가 병렬로 처리됨");
        log.info("");
    }

    // ==================== 테스트 4: 순서 보장 ====================

    @Test
    @Order(4)
    @DisplayName("테스트 4: 동일 couponId 내 순서 보장 (선착순 시나리오)")
    void test4_orderGuaranteeWithinSameCouponId() throws Exception {
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════════╗");
        log.info("║  테스트 4: 순서 보장 - 선착순 쿠폰 발급 시나리오                   ║");
        log.info("╚══════════════════════════════════════════════════════════════════╝");

        String uniqueTopic = COUPON_ISSUE_TOPIC + "-order-" + System.currentTimeMillis();

        // 새 토픽 생성
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA_CONTAINER.getBootstrapServers());
        try (AdminClient adminClient = AdminClient.create(props)) {
            NewTopic topic = new NewTopic(uniqueTopic, PARTITION_COUNT, (short) 1);
            adminClient.createTopics(Collections.singleton(topic)).all().get(30, TimeUnit.SECONDS);
        }

        Long couponId = 100L;
        int requestCount = 20;  // 선착순 20명 시나리오
        List<Long> receivedOrder = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(requestCount);

        log.info("[시나리오] 선착순 쿠폰(couponId={}) 발급 - {}명 동시 요청", couponId, requestCount);
        log.info("[기대결과] Kafka에 도착한 순서대로 Consumer가 처리 (순서 보장)");
        log.info("");

        // Consumer 시작
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "order-test-group-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<CouponIssueRequest> deserializer = new JsonDeserializer<>(CouponIssueRequest.class);
        deserializer.addTrustedPackages("*");

        DefaultKafkaConsumerFactory<String, CouponIssueRequest> testConsumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), deserializer);

        ContainerProperties containerProperties = new ContainerProperties(uniqueTopic);
        containerProperties.setMessageListener((MessageListener<String, CouponIssueRequest>) record -> {
            receivedOrder.add(record.value().userId());
            log.info("[Consumer] 순서 #{} - userId: {}, offset: {}, partition: {}",
                    receivedOrder.size(), record.value().userId(), record.offset(), record.partition());
            latch.countDown();
        });

        KafkaMessageListenerContainer<String, CouponIssueRequest> container =
                new KafkaMessageListenerContainer<>(testConsumerFactory, containerProperties);
        container.start();
        Thread.sleep(3000);

        // 순차적으로 메시지 발행 (userId 1, 2, 3, ... 순서)
        log.info("[Producer] 발행 순서: userId 1 → 2 → 3 → ... → {}", requestCount);
        List<Long> sentOrder = new ArrayList<>();

        for (int i = 1; i <= requestCount; i++) {
            Long userId = (long) i;
            String key = String.valueOf(couponId);  // couponId만 키로 사용 → 동일 파티션 → 순서 보장
            CouponIssueRequest request = CouponIssueRequest.of(
                    UUID.randomUUID().toString(),
                    couponId,
                    userId
            );

            SendResult<String, CouponIssueRequest> result =
                    kafkaTemplate.send(uniqueTopic, key, request).get(10, TimeUnit.SECONDS);
            sentOrder.add(userId);

            log.info("[Producer] 발행 #{} - userId: {}, partition: {}, offset: {}",
                    i, userId, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
        }

        // 수신 대기
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        container.stop();

        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────────┐");
        log.info("│ 순서 비교:                                                       │");
        log.info("│   발행 순서: {}                                    ", sentOrder);
        log.info("│   수신 순서: {}                                    ", receivedOrder);
        log.info("│   순서 일치: {}                                                 ", sentOrder.equals(receivedOrder) ? "✅ YES" : "❌ NO");
        log.info("└─────────────────────────────────────────────────────────────────┘");

        assertThat(completed).isTrue();
        assertThat(receivedOrder).isEqualTo(sentOrder);
        log.info("[검증 통과] ✅ 동일 couponId 내 메시지 순서 보장됨 (선착순 보장)");
        log.info("");
    }

    // ==================== 테스트 5: 중복 발급 방지 (멱등성) ====================

    @Test
    @Order(5)
    @DisplayName("테스트 5: 중복 요청 감지 - 동일 requestId 처리")
    void test5_duplicateRequestDetection() throws Exception {
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════════╗");
        log.info("║  테스트 5: 중복 발급 방지 - 멱등성 키(requestId) 활용             ║");
        log.info("╚══════════════════════════════════════════════════════════════════╝");

        String uniqueTopic = COUPON_ISSUE_TOPIC + "-idempotent-" + System.currentTimeMillis();

        // 새 토픽 생성
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA_CONTAINER.getBootstrapServers());
        try (AdminClient adminClient = AdminClient.create(props)) {
            NewTopic topic = new NewTopic(uniqueTopic, PARTITION_COUNT, (short) 1);
            adminClient.createTopics(Collections.singleton(topic)).all().get(30, TimeUnit.SECONDS);
        }

        String sameRequestId = "REQ-" + UUID.randomUUID();
        Long couponId = 200L;
        Long userId = 1L;
        int duplicateCount = 5;

        Map<String, AtomicInteger> requestIdCounts = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(duplicateCount);

        log.info("[시나리오] 동일 requestId로 {}번 중복 요청", duplicateCount);
        log.info("[설정] requestId: {}", sameRequestId);
        log.info("[기대결과] Consumer에서 requestId 기반 중복 감지 가능");
        log.info("");

        // Consumer 시작
        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "idempotent-test-group-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<CouponIssueRequest> deserializer = new JsonDeserializer<>(CouponIssueRequest.class);
        deserializer.addTrustedPackages("*");

        DefaultKafkaConsumerFactory<String, CouponIssueRequest> testConsumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), deserializer);

        ContainerProperties containerProperties = new ContainerProperties(uniqueTopic);
        containerProperties.setMessageListener((MessageListener<String, CouponIssueRequest>) record -> {
            String requestId = record.value().requestId();
            int count = requestIdCounts.computeIfAbsent(requestId, k -> new AtomicInteger(0)).incrementAndGet();

            if (count == 1) {
                log.info("[Consumer] ✅ 첫 번째 요청 처리 - requestId: {}", requestId);
            } else {
                log.info("[Consumer] ⚠️  중복 요청 감지! ({}번째) - requestId: {}", count, requestId);
            }

            latch.countDown();
        });

        KafkaMessageListenerContainer<String, CouponIssueRequest> container =
                new KafkaMessageListenerContainer<>(testConsumerFactory, containerProperties);
        container.start();
        Thread.sleep(3000);

        // 동일 requestId로 중복 발행
        log.info("[Producer] 동일 requestId로 {}번 발행", duplicateCount);
        for (int i = 0; i < duplicateCount; i++) {
            String key = couponId + ":" + userId;
            CouponIssueRequest request = new CouponIssueRequest(
                    sameRequestId,  // 동일한 requestId
                    couponId,
                    userId,
                    java.time.LocalDateTime.now()
            );

            SendResult<String, CouponIssueRequest> result =
                    kafkaTemplate.send(uniqueTopic, key, request).get(10, TimeUnit.SECONDS);

            log.info("[Producer] 발행 #{} - requestId: {}, partition: {}, offset: {}",
                    i + 1, sameRequestId, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
        }

        // 수신 대기
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        container.stop();

        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────────┐");
        log.info("│ 중복 요청 분석:                                                  │");
        requestIdCounts.forEach((reqId, count) ->
                log.info("│   requestId: {} → {}번 수신              ", reqId.substring(0, 20) + "...", count.get()));
        log.info("│                                                                 │");
        log.info("│ [결론] Consumer에서 requestId 기반 멱등성 체크로 중복 처리 방지  │");
        log.info("│        첫 번째 요청만 처리하고 나머지는 스킵해야 함              │");
        log.info("└─────────────────────────────────────────────────────────────────┘");

        assertThat(completed).isTrue();
        assertThat(requestIdCounts.get(sameRequestId).get()).isEqualTo(duplicateCount);
        log.info("[검증 통과] ✅ 중복 요청이 모두 Consumer에 도달 → 멱등성 체크 필요성 확인");
        log.info("");
    }

    // ==================== 테스트 6: 대규모 선착순 시나리오 ====================

    @Test
    @Order(6)
    @DisplayName("테스트 6: 대규모 선착순 시나리오 - 100명 동시 요청")
    void test6_largeConcurrentRequests() throws Exception {
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════════╗");
        log.info("║  테스트 6: 대규모 선착순 시나리오 - 100명 동시 요청               ║");
        log.info("╚══════════════════════════════════════════════════════════════════╝");

        String uniqueTopic = COUPON_ISSUE_TOPIC + "-large-" + System.currentTimeMillis();

        // 새 토픽 생성
        Properties props = new Properties();
        props.put("bootstrap.servers", KAFKA_CONTAINER.getBootstrapServers());
        try (AdminClient adminClient = AdminClient.create(props)) {
            NewTopic topic = new NewTopic(uniqueTopic, PARTITION_COUNT, (short) 1);
            adminClient.createTopics(Collections.singleton(topic)).all().get(30, TimeUnit.SECONDS);
        }

        Long couponId = 300L;
        int totalRequests = 100;
        int couponStock = 30;  // 선착순 30명만 발급 가능

        List<Long> processedOrder = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        log.info("[시나리오] 선착순 쿠폰 발급");
        log.info("[설정] 총 요청: {}명, 쿠폰 재고: {}개", totalRequests, couponStock);
        log.info("[기대결과] 먼저 도착한 {}명만 발급 성공, 나머지 {}명은 재고 소진", couponStock, totalRequests - couponStock);
        log.info("");

        // Consumer 시작 (재고 시뮬레이션)
        AtomicInteger remainingStock = new AtomicInteger(couponStock);

        Map<String, Object> consumerProps = new HashMap<>();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA_CONTAINER.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "large-test-group-" + System.currentTimeMillis());
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        JsonDeserializer<CouponIssueRequest> deserializer = new JsonDeserializer<>(CouponIssueRequest.class);
        deserializer.addTrustedPackages("*");

        DefaultKafkaConsumerFactory<String, CouponIssueRequest> testConsumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), deserializer);

        ContainerProperties containerProperties = new ContainerProperties(uniqueTopic);
        containerProperties.setMessageListener((MessageListener<String, CouponIssueRequest>) record -> {
            Long userId = record.value().userId();
            processedOrder.add(userId);

            // 재고 차감 시뮬레이션
            int stock = remainingStock.getAndDecrement();
            if (stock > 0) {
                successCount.incrementAndGet();
                if (successCount.get() <= 10 || successCount.get() == couponStock) {
                    log.info("[Consumer] ✅ 발급 성공 #{} - userId: {}, 남은 재고: {}",
                            successCount.get(), userId, stock - 1);
                }
            } else {
                failCount.incrementAndGet();
                if (failCount.get() <= 3) {
                    log.info("[Consumer] ❌ 재고 소진 - userId: {}", userId);
                }
            }

            latch.countDown();
        });

        KafkaMessageListenerContainer<String, CouponIssueRequest> container =
                new KafkaMessageListenerContainer<>(testConsumerFactory, containerProperties);
        container.start();
        Thread.sleep(3000);

        // 100명 동시 요청 (멀티스레드)
        log.info("[Producer] {}명 동시 요청 발행 시작...", totalRequests);
        long startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 1; i <= totalRequests; i++) {
            final long userId = i;
            futures.add(executor.submit(() -> {
                try {
                    String key = couponId + ":" + userId;
                    CouponIssueRequest request = CouponIssueRequest.of(
                            UUID.randomUUID().toString(),
                            couponId,
                            userId
                    );
                    kafkaTemplate.send(uniqueTopic, key, request).get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    log.error("발행 실패: userId={}", userId, e);
                }
            }));
        }

        // 모든 발행 완료 대기
        for (Future<?> future : futures) {
            future.get();
        }
        executor.shutdown();

        long publishTime = System.currentTimeMillis() - startTime;
        log.info("[Producer] {}건 발행 완료 (소요시간: {}ms)", totalRequests, publishTime);

        // 수신 대기
        boolean completed = latch.await(60, TimeUnit.SECONDS);
        container.stop();

        long totalTime = System.currentTimeMillis() - startTime;

        log.info("");
        log.info("┌─────────────────────────────────────────────────────────────────┐");
        log.info("│ 선착순 쿠폰 발급 결과:                                           │");
        log.info("│   총 요청: {}명                                                ", totalRequests);
        log.info("│   발급 성공: {}명 (선착순)                                     ", successCount.get());
        log.info("│   재고 소진: {}명                                              ", failCount.get());
        log.info("│   처리 순서: {} ... (처음 10명)                ", processedOrder.subList(0, Math.min(10, processedOrder.size())));
        log.info("│                                                                 │");
        log.info("│ 성능 지표:                                                      │");
        log.info("│   발행 소요시간: {}ms                                          ", publishTime);
        log.info("│   전체 소요시간: {}ms                                          ", totalTime);
        log.info("│   처리량: {} req/sec                                       ", (totalRequests * 1000) / totalTime);
        log.info("└─────────────────────────────────────────────────────────────────┘");

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(couponStock);
        assertThat(failCount.get()).isEqualTo(totalRequests - couponStock);
        log.info("[검증 통과] ✅ 선착순 {}명만 발급 성공, 나머지는 재고 소진 처리", couponStock);
        log.info("");
    }
}
