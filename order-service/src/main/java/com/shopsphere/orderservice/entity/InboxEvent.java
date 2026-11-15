package com.shopsphere.orderservice.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Marks an envelope event as applied by one handler, so a redelivery is recognised and skipped.  
@Entity
@Table(name = "order_inbox_events")
public class InboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(nullable = false, length = 100)
    private String consumer;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    protected InboxEvent() {
    }

    public InboxEvent(String eventId, String consumer, LocalDateTime processedAt) {
        this.eventId = eventId;
        this.consumer = consumer;
        this.processedAt = processedAt;
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getConsumer() { return consumer; }
    public LocalDateTime getProcessedAt() { return processedAt; }
}