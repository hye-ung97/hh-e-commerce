package org.hhplus.hhecommerce.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.common.DomainEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Spring의 ApplicationEventPublisher를 사용한 DomainEventPublisher 구현체.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringDomainEventPublisher implements DomainEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publish(Object event) {
        if (event == null) {
            log.warn("null 이벤트는 발행할 수 없습니다.");
            return;
        }

        log.debug("이벤트 발행 - type: {}", event.getClass().getSimpleName());
        applicationEventPublisher.publishEvent(event);
    }
}
