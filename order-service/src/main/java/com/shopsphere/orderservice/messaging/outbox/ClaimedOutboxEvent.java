package com.shopsphere.orderservice.messaging.outbox;

import com.shopsphere.orderservice.entity.OutboxEvent;

/** Detached copy of a claimed outbox row, safe to use outside the claiming transaction. */
public record ClaimedOutboxEvent(Long id, Long aggregateId, String topic, String messageKey, String eventId,
                                 String eventType, String correlationId, String payload, int attempts) {

    public static ClaimedOutboxEvent from(OutboxEvent event) {
        return new ClaimedOutboxEvent(
                event.getId(),
                event.getAggregateId(),
                event.getTopic(),
                event.getMessageKey() != null ? event.getMessageKey() : String.valueOf(event.getAggregateId()),
                event.getEventId(),
                event.getEventType(),
                event.getCorrelationId(),
                event.getPayload(),
                event.getAttempts() == null ? 0 : event.getAttempts());
    }
}
