package org.hhplus.hhecommerce.domain.common;

import java.util.List;

public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent outboxEvent);

    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.OutboxStatus status, int limit);

    List<OutboxEvent> findFailedEventsForRetry(int maxRetryCount, int limit);

    List<OutboxEvent> findStuckProcessingEvents(java.time.LocalDateTime threshold, int limit);

    int deletePublishedEventsBefore(java.time.LocalDateTime before);
}
