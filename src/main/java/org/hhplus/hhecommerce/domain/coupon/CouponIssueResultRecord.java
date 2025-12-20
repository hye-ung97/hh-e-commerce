package org.hhplus.hhecommerce.domain.coupon;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_issue_result",
       indexes = {
           @Index(name = "idx_request_id", columnList = "requestId", unique = true),
           @Index(name = "idx_coupon_user", columnList = "couponId, userId")
       })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueResultRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String requestId;

    @Column(nullable = false)
    private Long couponId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponIssueStatus status;

    private String message;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime processedAt;

    public CouponIssueResultRecord(String requestId, Long couponId, Long userId,
                                    CouponIssueStatus status, String message,
                                    LocalDateTime requestedAt) {
        this.requestId = requestId;
        this.couponId = couponId;
        this.userId = userId;
        this.status = status;
        this.message = message;
        this.requestedAt = requestedAt;
        this.processedAt = LocalDateTime.now();
    }

    public static CouponIssueResultRecord processing(CouponIssueRequest request) {
        return new CouponIssueResultRecord(
                request.requestId(),
                request.couponId(),
                request.userId(),
                CouponIssueStatus.PROCESSING,
                "처리 중",
                request.requestedAt()
        );
    }

    public void complete(CouponIssueStatus status, String message) {
        this.status = status;
        this.message = message;
        this.processedAt = LocalDateTime.now();
    }

    public boolean isProcessed() {
        return status != CouponIssueStatus.PROCESSING;
    }
}
