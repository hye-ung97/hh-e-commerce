package org.hhplus.hhecommerce.application.point;

import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.point.DeductRequest;
import org.hhplus.hhecommerce.api.dto.point.DeductResponse;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.point.exception.PointErrorCode;
import org.hhplus.hhecommerce.domain.point.exception.PointException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeductPointUseCase {

    private final PointRepository pointRepository;

    @Retryable(
        retryFor = OptimisticLockException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, maxDelay = 500, multiplier = 2)
    )
    @Transactional
    public DeductResponse execute(Long userId, DeductRequest request) {
        if (pointRepository.findByUserId(userId).isEmpty()) {
            throw new PointException(PointErrorCode.POINT_NOT_FOUND);
        }

        int deductAmount = request.getAmount();
        validateDeductAmount(deductAmount);

        int updated = pointRepository.deductPoint(userId, deductAmount);
        if (updated == 0) {
            throw new PointException(PointErrorCode.INSUFFICIENT_BALANCE);
        }

        Point updatedPoint = pointRepository.findByUserId(userId)
                .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND));

        return new DeductResponse(
                updatedPoint.getId(),
                userId,
                updatedPoint.getAmount(),
                updatedPoint.getCreatedAt(),
                updatedPoint.getUpdatedAt(),
                deductAmount,
                "Point deducted successfully"
        );
    }

    @Recover
    public DeductResponse recoverFromOptimisticLock(OptimisticLockException e, Long userId, DeductRequest request) {
        log.error("포인트 차감 재시도 실패 - userId: {}, amount: {}", userId, request.getAmount(), e);
        throw new PointException(PointErrorCode.POINT_UPDATE_FAILED);
    }

    private void validateDeductAmount(int amount) {
        if (amount <= 0) {
            throw new PointException(PointErrorCode.INVALID_AMOUNT);
        }
        if (amount % 100 != 0) {
            throw new PointException(PointErrorCode.INVALID_USE_UNIT);
        }
        if (amount < 1000) {
            throw new PointException(PointErrorCode.BELOW_MIN_USE_AMOUNT);
        }
    }
}
