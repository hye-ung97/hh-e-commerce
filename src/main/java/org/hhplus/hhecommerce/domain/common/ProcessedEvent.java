package org.hhplus.hhecommerce.domain.common;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "processed_event", indexes = {
    @Index(name = "idx_processed_event_id", columnList = "eventId", unique = true)
})
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = LocalDateTime.now();
    }

    public static String generateEventId(String aggregateType, Long aggregateId, String eventType) {
        return String.format("%s:%d:%s", aggregateType, aggregateId, eventType);
    }
}
