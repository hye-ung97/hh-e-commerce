package org.hhplus.hhecommerce.domain.coupon;

import jakarta.persistence.*;
import lombok.Getter;
import org.hhplus.hhecommerce.domain.common.BaseTimeEntity;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "failed_coupon_rollback", indexes = {
    @Index(name = "idx_status_created", columnList = "status, createdAt"),
    @Index(name = "idx_coupon_user", columnList = "couponId, userId")
})
public class FailedCouponRollback extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long couponId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RollbackFailureStatus status;

    @Column(length = 500)
    private String originalError;

    @Column(length = 500)
    private String rollbackError;

    @Column
    private LocalDateTime resolvedAt;

    @Column(length = 200)
    private String resolvedBy;

    @Column
    private int retryCount;

    protected FailedCouponRollback() {
        super();
    }

    public FailedCouponRollback(Long couponId, Long userId, String originalError, String rollbackError) {
        super();
        this.couponId = couponId;
        this.userId = userId;
        this.originalError = truncate(originalError, 500);
        this.rollbackError = truncate(rollbackError, 500);
        this.status = RollbackFailureStatus.PENDING;
        this.retryCount = 0;
    }

    public void resolve(String resolvedBy) {
        this.status = RollbackFailureStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
        this.resolvedBy = resolvedBy;
    }

    public void ignore(String resolvedBy) {
        this.status = RollbackFailureStatus.IGNORED;
        this.resolvedAt = LocalDateTime.now();
        this.resolvedBy = resolvedBy;
    }

    public void incrementRetryCount() {
        this.retryCount++;
    }

    public boolean canRetry(int maxRetryCount) {
        return this.status == RollbackFailureStatus.PENDING && this.retryCount < maxRetryCount;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    public enum RollbackFailureStatus {
        PENDING,
        RESOLVED,
        IGNORED
    }
}
