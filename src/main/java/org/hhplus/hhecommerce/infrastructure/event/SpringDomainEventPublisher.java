package org.hhplus.hhecommerce.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hhplus.hhecommerce.domain.common.DomainEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "event.publisher.type", havingValue = "spring", matchIfMissing = true)
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
