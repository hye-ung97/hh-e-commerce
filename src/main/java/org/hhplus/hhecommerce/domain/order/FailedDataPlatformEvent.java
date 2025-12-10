package org.hhplus.hhecommerce.domain.order;

import jakarta.persistence.*;
import lombok.Getter;
import org.hhplus.hhecommerce.domain.common.BaseTimeEntity;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "failed_data_platform_event", indexes = {
    @Index(name = "idx_fdpe_status_created", columnList = "status, createdAt"),
    @Index(name = "idx_fdpe_order_id", columnList = "orderId")
})
public class FailedDataPlatformEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

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

    protected FailedDataPlatformEvent() {
        super();
    }

    public FailedDataPlatformEvent(Long orderId, Long userId, String eventPayload, String errorMessage) {
        super();
        this.orderId = orderId;
        this.userId = userId;
        this.eventPayload = eventPayload;
        this.errorMessage = truncate(errorMessage, 500);
        this.status = RetryStatus.PENDING;
        this.retryCount = 0;
    }

    public void startRetry() {
        this.status = RetryStatus.RETRY_IN_PROGRESS;
        this.lastRetryAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = RetryStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String errorMessage) {
        this.retryCount++;
        this.errorMessage = truncate(errorMessage, 500);
        this.status = RetryStatus.PENDING;
    }

    public void markAsFailed() {
        this.status = RetryStatus.FAILED;
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
