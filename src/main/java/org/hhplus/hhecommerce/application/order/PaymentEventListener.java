package org.hhplus.hhecommerce.application.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.common.RejectedAsyncTask;
import org.hhplus.hhecommerce.domain.common.RejectedAsyncTaskRepository;
import org.hhplus.hhecommerce.domain.order.PaymentCompletedEvent;
import org.hhplus.hhecommerce.infrastructure.external.ResilientExternalDataPlatformClient;
import org.hhplus.hhecommerce.infrastructure.external.ResilientNotificationClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
@ConditionalOnProperty(name = "event.publisher.type", havingValue = "spring", matchIfMissing = true)
public class PaymentEventListener {

    public static final String TASK_TYPE_DATA_PLATFORM = "DATA_PLATFORM_TRANSFER";
    public static final String TASK_TYPE_NOTIFICATION = "NOTIFICATION";
    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final ResilientExternalDataPlatformClient externalDataPlatformClient;
    private final ResilientNotificationClient notificationClient;
    private final Executor taskExecutor;
    private final RejectedAsyncTaskRepository rejectedAsyncTaskRepository;
    private final ObjectMapper objectMapper;

    @Value("${dlq.fallback.file-path:./dlq-fallback}")
    private String dlqFallbackPath;

    public PaymentEventListener(
            ResilientExternalDataPlatformClient externalDataPlatformClient,
            ResilientNotificationClient notificationClient,
            @Qualifier("taskExecutor") Executor taskExecutor,
            RejectedAsyncTaskRepository rejectedAsyncTaskRepository,
            ObjectMapper objectMapper) {
        this.externalDataPlatformClient = externalDataPlatformClient;
        this.notificationClient = notificationClient;
        this.taskExecutor = taskExecutor;
        this.rejectedAsyncTaskRepository = rejectedAsyncTaskRepository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try {
            Path fallbackDir = Paths.get(dlqFallbackPath);
            if (!Files.exists(fallbackDir)) {
                Files.createDirectories(fallbackDir);
                log.info("DLQ 폴백 디렉토리 생성: {}", fallbackDir.toAbsolutePath());
            }
        } catch (IOException e) {
            log.warn("DLQ 폴백 디렉토리 생성 실패: {}", e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleDataPlatformTransfer(PaymentCompletedEvent event) {
        try {
            taskExecutor.execute(() -> processDataPlatformTransfer(event));
        } catch (RejectedExecutionException e) {
            log.warn("데이터 플랫폼 전송 작업 거부됨 - DLQ에 저장합니다. orderId: {}", event.orderId());
            saveToDeadLetterQueue(event, TASK_TYPE_DATA_PLATFORM, e.getMessage());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleNotification(PaymentCompletedEvent event) {
        if (event.userPhone() == null || event.userPhone().isBlank()) {
            log.info("알림톡 발송 생략 - orderId: {}, 전화번호 없음", event.orderId());
            return;
        }

        try {
            taskExecutor.execute(() -> processNotification(event));
        } catch (RejectedExecutionException e) {
            log.warn("알림톡 발송 작업 거부됨 - DLQ에 저장합니다. orderId: {}", event.orderId());
            saveToDeadLetterQueue(event, TASK_TYPE_NOTIFICATION, e.getMessage());
        }
    }

    private void processDataPlatformTransfer(PaymentCompletedEvent event) {
        log.debug("데이터 플랫폼 전송 시작 - orderId: {}", event.orderId());

        long startTime = System.currentTimeMillis();
        try {
            externalDataPlatformClient.sendOrderData(event);

            long duration = System.currentTimeMillis() - startTime;
            log.info("데이터 플랫폼 전송 완료 - orderId: {}, duration: {}ms", event.orderId(), duration);

        } catch (Exception e) {
            log.error("데이터 플랫폼 전송 실패 (최종) - orderId: {}, error: {}",
                    event.orderId(), e.getMessage());
        }
    }

    private void processNotification(PaymentCompletedEvent event) {
        log.debug("알림톡 발송 시작 - orderId: {}", event.orderId());

        long startTime = System.currentTimeMillis();
        try {
            notificationClient.sendOrderConfirmation(event);

            long duration = System.currentTimeMillis() - startTime;
            log.info("알림톡 발송 완료 - orderId: {}, duration: {}ms", event.orderId(), duration);

        } catch (Exception e) {
            log.error("알림톡 발송 실패 (최종) - orderId: {}, error: {}",
                    event.orderId(), e.getMessage());
        }
    }

    private void saveToDeadLetterQueue(PaymentCompletedEvent event, String taskType, String errorMessage) {
        String eventPayload;
        try {
            eventPayload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("[CRITICAL] DLQ 저장 실패 - 이벤트 직렬화 오류. taskType: {}, orderId: {}, error: {}",
                    taskType, event.orderId(), e.getMessage());
            return;
        }

        // 1차 시도: DB 저장
        try {
            RejectedAsyncTask rejectedTask = new RejectedAsyncTask(taskType, eventPayload, errorMessage);
            rejectedAsyncTaskRepository.save(rejectedTask);
            log.info("DLQ에 저장 완료 - taskType: {}, orderId: {}", taskType, event.orderId());
            return;
        } catch (Exception e) {
            log.warn("DLQ DB 저장 실패 - 파일 폴백 시도. taskType: {}, orderId: {}, error: {}",
                    taskType, event.orderId(), e.getMessage());
        }

        // 2차 시도: 파일 폴백
        if (!saveToFallbackFile(event, taskType, eventPayload, errorMessage)) {
            // 최종 실패: CRITICAL 로그 (알림 시스템 연동용)
            log.error("[CRITICAL] DLQ 저장 완전 실패 - 수동 복구 필요. taskType: {}, orderId: {}, userId: {}, totalAmount: {}, payload: {}",
                    taskType, event.orderId(), event.userId(), event.totalAmount(), eventPayload);
        }
    }

    private boolean saveToFallbackFile(PaymentCompletedEvent event, String taskType, String eventPayload, String errorMessage) {
        try {
            String timestamp = LocalDateTime.now().format(FILE_DATE_FORMAT);
            String fileName = String.format("%s_%s_%d.json", taskType, timestamp, event.orderId());
            Path filePath = Paths.get(dlqFallbackPath, fileName);

            String content = String.format("""
                    {
                      "taskType": "%s",
                      "orderId": %d,
                      "userId": %d,
                      "errorMessage": "%s",
                      "createdAt": "%s",
                      "event": %s
                    }
                    """,
                    taskType, event.orderId(), event.userId(),
                    errorMessage != null ? errorMessage.replace("\"", "\\\"") : "",
                    LocalDateTime.now(),
                    eventPayload);

            Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            log.warn("DLQ 파일 폴백 저장 완료 - taskType: {}, orderId: {}, file: {}",
                    taskType, event.orderId(), filePath.toAbsolutePath());
            return true;
        } catch (IOException e) {
            log.error("DLQ 파일 폴백 저장 실패 - taskType: {}, orderId: {}, error: {}",
                    taskType, event.orderId(), e.getMessage());
            return false;
        }
    }
}
