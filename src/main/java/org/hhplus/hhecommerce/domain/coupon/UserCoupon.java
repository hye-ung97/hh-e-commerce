package org.hhplus.hhecommerce.domain.coupon;

import lombok.Getter;
import lombok.Setter;
import org.hhplus.hhecommerce.domain.common.BaseTimeEntity;
import java.time.LocalDateTime;

@Getter
public class UserCoupon extends BaseTimeEntity {
    @Setter
    private Long id;
    private Long userId;
    private Long couponId;
    private CouponStatus status;
    private LocalDateTime usedAt;
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
}
