package org.hhplus.hhecommerce.application.order;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.common.RejectedAsyncTask;
import org.hhplus.hhecommerce.domain.common.RejectedAsyncTaskRepository;
import org.hhplus.hhecommerce.domain.order.PaymentCompletedEvent;
import org.hhplus.hhecommerce.infrastructure.external.ResilientExternalDataPlatformClient;
import org.hhplus.hhecommerce.infrastructure.external.ResilientNotificationClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
public class PaymentEventListener {

    public static final String TASK_TYPE_DATA_PLATFORM = "DATA_PLATFORM_TRANSFER";
    public static final String TASK_TYPE_NOTIFICATION = "NOTIFICATION";

    private final ResilientExternalDataPlatformClient externalDataPlatformClient;
    private final ResilientNotificationClient notificationClient;
    private final Executor taskExecutor;
    private final RejectedAsyncTaskRepository rejectedAsyncTaskRepository;
    private final ObjectMapper objectMapper;

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
        try {
            String eventPayload = objectMapper.writeValueAsString(event);
            RejectedAsyncTask rejectedTask = new RejectedAsyncTask(taskType, eventPayload, errorMessage);
            rejectedAsyncTaskRepository.save(rejectedTask);
            log.info("DLQ에 저장 완료 - taskType: {}, orderId: {}", taskType, event.orderId());
        } catch (JsonProcessingException e) {
            log.error("DLQ 저장 실패 - 이벤트 직렬화 오류. taskType: {}, orderId: {}, error: {}",
                    taskType, event.orderId(), e.getMessage());
        } catch (Exception e) {
            log.error("DLQ 저장 실패 - taskType: {}, orderId: {}, error: {}",
                    taskType, event.orderId(), e.getMessage());
        }
    }
}
