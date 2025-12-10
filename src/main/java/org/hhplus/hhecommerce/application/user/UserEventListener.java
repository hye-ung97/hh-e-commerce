package org.hhplus.hhecommerce.application.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.point.Point;
import org.hhplus.hhecommerce.domain.point.PointRepository;
import org.hhplus.hhecommerce.domain.user.UserCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventListener {

    private final PointRepository pointRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleUserCreated(UserCreatedEvent event) {
        Long userId = event.userId();

        if (pointRepository.findByUserId(userId).isPresent()) {
            log.warn("포인트가 이미 존재합니다 - userId: {}", userId);
            return;
        }

        Point point = pointRepository.save(new Point(userId));
        log.info("초기 포인트 생성 완료 - userId: {}, pointId: {}", userId, point.getId());
    }
}
