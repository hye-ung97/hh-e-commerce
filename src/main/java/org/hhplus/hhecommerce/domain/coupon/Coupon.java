package org.hhplus.hhecommerce.domain.coupon;

import lombok.Getter;
import org.hhplus.hhecommerce.domain.common.BaseTimeEntity;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponErrorCode;
import org.hhplus.hhecommerce.domain.coupon.exception.CouponException;

import java.time.LocalDateTime;

@Getter
public class Coupon extends BaseTimeEntity {
    private Long id;
    private String name;
    private CouponType discountType;
    private int discountValue;
    private Integer maxDiscountAmount;  // 할인율인 경우 최대 할인 금액
    private int minOrderAmount;
    private int totalQuantity;
    private int issuedQuantity;
    private LocalDateTime startAt;
    private LocalDateTime endAt;

    protected Coupon() { super(); }

    public Coupon(String name, CouponType discountType, int discountValue, Integer maxDiscountAmount,
                  int minOrderAmount, int totalQuantity, LocalDateTime startAt, LocalDateTime endAt) {
        super();
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountAmount = maxDiscountAmount;
        this.minOrderAmount = minOrderAmount;
        this.totalQuantity = totalQuantity;
        this.issuedQuantity = 0;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public synchronized boolean canIssue() {
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(startAt) && now.isBefore(endAt)
               && issuedQuantity < totalQuantity;
    }

    public synchronized void issue() {
        if (!canIssue()) {
            throw new CouponException(CouponErrorCode.COUPON_UNAVAILABLE);
        }
        issuedQuantity++;
        updateTimestamp();
    }

    public int calculateDiscount(int orderAmount) {
        if (discountType == CouponType.RATE) {
            int discount = orderAmount * discountValue / 100;
            if (maxDiscountAmount != null && discount > maxDiscountAmount) {
                return maxDiscountAmount;
            }
            return discount;
        }
        return discountValue;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
