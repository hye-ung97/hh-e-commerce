package org.hhplus.hhecommerce.domain.coupon;

import jakarta.persistence.*;
import lombok.Getter;
import org.hhplus.hhecommerce.domain.common.BaseTimeEntity;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "USER_COUPON",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_user_coupon",
        columnNames = {"user_id", "coupon_id"}
    ),
    indexes = {
        @Index(name = "idx_user_id", columnList = "user_id"),
        @Index(name = "idx_coupon_id", columnList = "coupon_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_expired_at", columnList = "expired_at")
    }
)
public class UserCoupon extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponStatus status;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    protected UserCoupon() { super(); }

    public UserCoupon(Long userId, Long couponId, LocalDateTime expiredAt) {
        super();
        this.userId = userId;
        this.couponId = couponId;
        this.status = CouponStatus.AVAILABLE;
        this.expiredAt = expiredAt;
    }

    public void use() {
        if (status != CouponStatus.AVAILABLE) {
            throw new IllegalStateException("사용할 수 없는 쿠폰입니다");
        }
        this.status = CouponStatus.USED;
        this.usedAt = LocalDateTime.now();
        updateTimestamp();
    }

    public void setId(Long id) {
        this.id = id;
    }
}
