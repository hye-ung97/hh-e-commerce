package org.hhplus.hhecommerce.domain.common;

import java.util.List;

public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent outboxEvent);

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status, int limit);

    List<OutboxEvent> findFailedEventsForRetry(int maxRetryCount, int limit);

    int deletePublishedEventsBefore(java.time.LocalDateTime before);
}
