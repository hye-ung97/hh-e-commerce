package org.hhplus.hhecommerce.application.point;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.point.ChargeRequest;
import org.hhplus.hhecommerce.api.dto.point.ChargeResponse;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.point.exception.PointErrorCode;
import org.hhplus.hhecommerce.domain.point.exception.PointException;
import org.hhplus.hhecommerce.domain.user.UserRepository;
import org.hhplus.hhecommerce.domain.user.exception.UserErrorCode;
import org.hhplus.hhecommerce.domain.user.exception.UserException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargePointUseCase {

    private final PointRepository pointRepository;
    private final UserRepository userRepository;

    @Retryable(
        retryFor = OptimisticLockException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 500, multiplier = 2)
    )
    @Transactional
    public ChargeResponse execute(Long userId, ChargeRequest request) {
        if (!userRepository.existsById(userId)) {
            throw new UserException(UserErrorCode.USER_NOT_FOUND);
        }

        int chargeAmount = request.getAmount();
        validateChargeAmount(chargeAmount);

        if (pointRepository.findByUserId(userId).isEmpty()) {
            pointRepository.save(new Point(userId));
        }

        int updatedPoint = pointRepository.chargePoint(userId, chargeAmount);
        if (updatedPoint == 0) {
            throw new PointException(PointErrorCode.EXCEED_MAX_BALANCE);
        }

        Point point = pointRepository.findByUserId(userId)
                .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND));

        return new ChargeResponse(
                point.getId(),
                userId,
                point.getAmount(),
                point.getCreatedAt(),
                point.getUpdatedAt(),
                chargeAmount,
                "Point charged successfully"
        );
    }

    @Recover
    public ChargeResponse recoverFromOptimisticLock(OptimisticLockException e, Long userId, ChargeRequest request) {
        log.error("포인트 충전 재시도 실패 - userId: {}, amount: {}", userId, request.getAmount(), e);
        throw new PointException(PointErrorCode.POINT_UPDATE_FAILED);
    }

    private void validateChargeAmount(int amount) {
        if (amount <= 0) {
            throw new PointException(PointErrorCode.INVALID_AMOUNT);
        }
        if (amount % 100 != 0) {
            throw new PointException(PointErrorCode.INVALID_CHARGE_UNIT);
        }
    }
}
