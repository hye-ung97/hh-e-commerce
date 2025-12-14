package org.hhplus.hhecommerce.domain.common;

import java.util.Collection;

/**
 * 도메인 이벤트 발행을 추상화한 인터페이스.
 * 서비스 레이어에서 Spring의 ApplicationEventPublisher에 직접 의존하지 않도록 합니다.
 */
public interface DomainEventPublisher {

    /**
     * 단일 이벤트를 발행합니다.
     *
     * @param event 발행할 이벤트 객체
     */
    void publish(Object event);

    /**
     * 여러 이벤트를 순차적으로 발행합니다.
     *
     * @param events 발행할 이벤트 컬렉션
     */
    default void publishAll(Collection<?> events) {
        if (events != null) {
            events.forEach(this::publish);
        }
    }
}
