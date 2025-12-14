package org.hhplus.hhecommerce.domain.common;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "rejected_async_task", indexes = {
    @Index(name = "idx_rat_status_created", columnList = "status, createdAt"),
    @Index(name = "idx_rat_task_type", columnList = "taskType, status")
})
public class RejectedAsyncTask extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String taskType;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String eventPayload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RetryStatus status;

    @Column(length = 500)
    private String errorMessage;

    @Column
    private int retryCount;

    @Column
    private LocalDateTime lastRetryAt;

    @Column
    private LocalDateTime completedAt;

    protected RejectedAsyncTask() {
        super();
    }

    public RejectedAsyncTask(String taskType, String eventPayload, String errorMessage) {
        super();
        this.taskType = taskType;
        this.eventPayload = eventPayload;
        this.errorMessage = truncate(errorMessage, 500);
        this.status = RetryStatus.PENDING;
        this.retryCount = 0;
    }

    public void startRetry() {
        this.status = RetryStatus.RETRY_IN_PROGRESS;
        this.lastRetryAt = LocalDateTime.now();
        updateTimestamp();
    }

    public void complete() {
        this.status = RetryStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        updateTimestamp();
    }

    public void fail(String errorMessage) {
        this.retryCount++;
        this.errorMessage = truncate(errorMessage, 500);
        this.status = RetryStatus.PENDING;
        updateTimestamp();
    }

    public void markAsFailed() {
        this.status = RetryStatus.FAILED;
        updateTimestamp();
    }

    public boolean canRetry(int maxRetryCount) {
        return this.status == RetryStatus.PENDING && this.retryCount < maxRetryCount;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    public enum RetryStatus {
        PENDING,
        RETRY_IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
