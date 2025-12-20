package org.hhplus.hhecommerce.infrastructure.persistence;

import org.hhplus.hhecommerce.domain.common.ProcessedEvent;
import org.hhplus.hhecommerce.domain.common.ProcessedEventRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaProcessedEventRepository extends JpaRepository<ProcessedEvent, Long>, ProcessedEventRepository {

    @Override
    boolean existsByEventId(String eventId);
}
