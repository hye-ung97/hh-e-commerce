package org.hhplus.hhecommerce.application.point;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.point.DeductRequest;
import org.hhplus.hhecommerce.api.dto.point.DeductResponse;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.point.exception.PointErrorCode;
import org.hhplus.hhecommerce.domain.point.exception.PointException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeductPointUseCase {

    private final PointRepository pointRepository;

    public DeductResponse execute(Long userId, DeductRequest request) {
        Point point = pointRepository.findByUserId(userId)
                .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND));

        point.deduct(request.getAmount());

        pointRepository.save(point);

        return DeductResponse.builder()
                .id(point.getId())
                .userId(userId)
                .amount(point.getAmount())
                .deductedAmount(request.getAmount())
                .message("Point deducted successfully")
                .createdAt(point.getCreatedAt())
                .updatedAt(point.getUpdatedAt())
                .build();
    }
}
