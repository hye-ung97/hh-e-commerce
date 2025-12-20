package org.hhplus.hhecommerce.infrastructure.persistence;

import org.hhplus.hhecommerce.domain.common.OutboxEvent;
import org.hhplus.hhecommerce.domain.common.OutboxEventRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface JpaOutboxEventRepository extends JpaRepository<OutboxEvent, Long>, OutboxEventRepository {

    @Override
    @Query("SELECT o FROM OutboxEvent o WHERE o.status = :status ORDER BY o.createdAt ASC LIMIT :limit")
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(
            @Param("status") OutboxEvent.OutboxStatus status,
            @Param("limit") int limit);

    @Override
    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'FAILED' AND o.retryCount < :maxRetryCount ORDER BY o.createdAt ASC LIMIT :limit")
    List<OutboxEvent> findFailedEventsForRetry(
            @Param("maxRetryCount") int maxRetryCount,
            @Param("limit") int limit);

    @Override
    @Modifying
    @Query("DELETE FROM OutboxEvent o WHERE o.status = 'PUBLISHED' AND o.publishedAt < :before")
    int deletePublishedEventsBefore(@Param("before") LocalDateTime before);
}
