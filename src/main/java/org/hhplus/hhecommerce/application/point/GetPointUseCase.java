package org.hhplus.hhecommerce.application.point;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.point.PointResponse;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.point.exception.PointErrorCode;
import org.hhplus.hhecommerce.domain.point.exception.PointException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GetPointUseCase {

    private final PointRepository pointRepository;

    public PointResponse execute(Long userId) {
        Point point = pointRepository.findByUserId(userId)
                .orElseThrow(() -> new PointException(PointErrorCode.POINT_NOT_FOUND));

        return new PointResponse(
                point.getId(),
                userId,
                point.getAmount(),
                point.getCreatedAt(),
                point.getUpdatedAt()
        );
    }
}
