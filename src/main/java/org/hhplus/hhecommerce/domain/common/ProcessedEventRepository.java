package org.hhplus.hhecommerce.domain.common;

public interface ProcessedEventRepository {

    boolean existsByEventId(String eventId);

    ProcessedEvent save(ProcessedEvent processedEvent);
}
