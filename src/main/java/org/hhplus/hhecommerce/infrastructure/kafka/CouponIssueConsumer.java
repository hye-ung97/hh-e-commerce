package org.hhplus.hhecommerce.infrastructure.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.coupon.*;
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
public class CouponIssueConsumer {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final CouponIssueResultService couponIssueResultService;

    @KafkaListener(
            topics = "${kafka.topic.coupon-issue-request}",
            groupId = "coupon-issue-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(
            @Payload CouponIssueRequest request,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_KEY) String key
    ) {
        log.info("[Kafka Consumer] 쿠폰 발급 요청 수신 - topic: {}, partition: {}, offset: {}, key: {}, requestId: {}",
                topic, partition, offset, key, request.requestId());

        // 1. 멱등성 체크
        if (isAlreadyProcessed(request.requestId())) {
            log.info("[Kafka Consumer] 이미 처리된 요청 - requestId: {}", request.requestId());
            return;
        }

        // 2. 쿠폰 존재 여부 확인
        var couponOptional = couponRepository.findById(request.couponId());
        if (couponOptional.isEmpty()) {
            saveResult(request, CouponIssueStatus.COUPON_NOT_FOUND, "쿠폰을 찾을 수 없습니다.");
            return;
        }
        Coupon coupon = couponOptional.get();

        // 3. 중복 발급 체크
        if (hasAlreadyIssued(request.couponId(), request.userId())) {
            saveResult(request, CouponIssueStatus.ALREADY_ISSUED, "이미 발급받은 쿠폰입니다.");
            return;
        }

        // 4. 재고 확인 및 발급
        try {
            int updated = couponRepository.increaseIssuedQuantity(request.couponId());
            if (updated == 0) {
                saveResult(request, CouponIssueStatus.OUT_OF_STOCK, "쿠폰 재고가 소진되었습니다.");
                return;
            }

            // 5. UserCoupon 저장
            UserCoupon userCoupon = new UserCoupon(request.userId(), request.couponId(), coupon.getEndAt());
            userCouponRepository.save(userCoupon);

            saveResult(request, CouponIssueStatus.SUCCESS, "쿠폰 발급이 완료되었습니다.");
            log.info("[Kafka Consumer] 쿠폰 발급 성공 - couponId: {}, userId: {}, requestId: {}",
                    request.couponId(), request.userId(), request.requestId());

        } catch (Exception e) {
            log.error("[Kafka Consumer] 쿠폰 발급 실패 - couponId: {}, userId: {}, error: {}",
                    request.couponId(), request.userId(), e.getMessage(), e);
            saveResult(request, CouponIssueStatus.FAILED, "쿠폰 발급 처리 중 오류가 발생했습니다.");
            throw e;
        }
    }

    private boolean isAlreadyProcessed(String requestId) {
        return couponIssueResultService.existsByRequestId(requestId);
    }

    private boolean hasAlreadyIssued(Long couponId, Long userId) {
        return userCouponRepository.existsByUserIdAndCouponId(userId, couponId);
    }

    private void saveResult(CouponIssueRequest request, CouponIssueStatus status, String message) {
        couponIssueResultService.saveResult(request, status, message);
    }
}
