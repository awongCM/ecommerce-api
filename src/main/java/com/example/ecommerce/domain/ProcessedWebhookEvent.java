package com.example.ecommerce.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "processed_webhook_events")
public class ProcessedWebhookEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt = LocalDateTime.now();

    protected ProcessedWebhookEvent() {}

    public ProcessedWebhookEvent(String eventId) {
        this.eventId = eventId;
    }

    public String getEventId() { return eventId; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}
