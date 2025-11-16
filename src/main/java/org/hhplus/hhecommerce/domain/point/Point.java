package org.hhplus.hhecommerce.domain.point;

import jakarta.persistence.*;
import lombok.Getter;
import org.hhplus.hhecommerce.domain.common.BaseTimeEntity;
import org.hhplus.hhecommerce.domain.point.exception.PointErrorCode;
import org.hhplus.hhecommerce.domain.point.exception.PointException;

@Getter
@Entity
@Table(name = "POINT",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_id", columnNames = {"user_id"})
    },
    indexes = {
        @Index(name = "idx_user_id", columnList = "user_id")
    }
)
public class Point extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private int amount;

    @Version
    private Long version;

    protected Point() {
        super();
    }

    public Point(Long userId) {
        super();
        this.userId = userId;
        this.amount = 0;
    }

    public Point(Long id, Long userId, int amount) {
        super();
        this.id = id;
        this.userId = userId;
        this.amount = amount;
    }

    public void charge(int chargeAmount) {
        validateAmount(chargeAmount);
        validateChargeUnit(chargeAmount);
        
        int newAmount = this.amount + chargeAmount;
        validateMaxBalance(newAmount);

        this.amount = newAmount;
        updateTimestamp();
    }

    public void deduct(int deductAmount) {
        validateAmount(deductAmount);
        validateUseUnit(deductAmount);

        if (this.amount < deductAmount) {
            throw new PointException(PointErrorCode.INSUFFICIENT_BALANCE);
        }

        validateMinUseAmount(deductAmount);

        this.amount -= deductAmount;
        updateTimestamp();
    }

    public boolean hasEnoughPoint(int requiredAmount) {
        return this.amount >= requiredAmount;
    }

    private void validateAmount(int amount) {
        if (amount <= 0) {
            throw new PointException(PointErrorCode.INVALID_AMOUNT);
        }
    }

    private void validateChargeUnit(int amount) {
        if (amount % 100 != 0) {
            throw new PointException(PointErrorCode.INVALID_CHARGE_UNIT);
        }
    }

    private void validateMaxBalance(int amount) {
        if (amount > 100000) {
            throw new PointException(PointErrorCode.EXCEED_MAX_BALANCE);
        }
    }

    private void validateUseUnit(int amount) {
        if (amount % 100 != 0) {
            throw new PointException(PointErrorCode.INVALID_USE_UNIT);
        }
    }

    private void validateMinUseAmount(int amount) {
        if (amount < 1000) {
            throw new PointException(PointErrorCode.BELOW_MIN_USE_AMOUNT);
        }
    }

    public void setId(Long id) {
        this.id = id;
    }
}
