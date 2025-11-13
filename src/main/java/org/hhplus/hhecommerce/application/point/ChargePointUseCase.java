package org.hhplus.hhecommerce.application.point;

import lombok.RequiredArgsConstructor;
import org.hhplus.hhecommerce.api.dto.point.ChargeRequest;
import org.hhplus.hhecommerce.api.dto.point.ChargeResponse;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.infrastructure.repository.point.PointRepository;
import org.hhplus.hhecommerce.domain.user.User;
import org.hhplus.hhecommerce.domain.user.exception.UserErrorCode;
import org.hhplus.hhecommerce.infrastructure.repository.user.UserRepository;
import org.hhplus.hhecommerce.domain.user.exception.UserException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChargePointUseCase {

    private final PointRepository pointRepository;
    private final UserRepository userRepository;

    public ChargeResponse execute(Long userId, ChargeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));

        Point point = pointRepository.findByUserId(userId)
                .orElseGet(() -> {
                    Point newPoint = new Point(user);
                    return pointRepository.save(newPoint);
                });

        point.charge(request.getAmount());

        pointRepository.save(point);

        return new ChargeResponse(
                point.getId(),
                userId,
                point.getAmount(),
                point.getCreatedAt(),
                point.getUpdatedAt(),
                request.getAmount(),
                "Point charged successfully"
        );
    }
}
