package org.hhplus.hhecommerce.domain.common;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "outbox_event", indexes = {
    @Index(name = "idx_outbox_status_created", columnList = "status, createdAt"),
    @Index(name = "idx_outbox_aggregate", columnList = "aggregateType, aggregateId")
})
public class OutboxEvent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String aggregateType;

    @Column(nullable = false)
    private Long aggregateId;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column
    private int retryCount;

    @Column
    private LocalDateTime publishedAt;

    @Column(length = 500)
    private String errorMessage;

    protected OutboxEvent() {
        super();
    }

    public OutboxEvent(String aggregateType, Long aggregateId, String eventType, String payload) {
        super();
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
    }

    public void markAsProcessing() {
        this.status = OutboxStatus.PROCESSING;
        updateTimestamp();
    }

    public void markAsPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        updateTimestamp();
    }

    public void markAsFailed(String errorMessage) {
        this.retryCount++;
        this.errorMessage = truncate(errorMessage, 500);
        this.status = OutboxStatus.FAILED;
        updateTimestamp();
    }

    public void retry() {
        this.status = OutboxStatus.PENDING;
        updateTimestamp();
    }

    public boolean canRetry(int maxRetryCount) {
        return this.retryCount < maxRetryCount;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    public enum OutboxStatus {
        PENDING,
        PROCESSING,
        PUBLISHED,
        FAILED
    }
}
