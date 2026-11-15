package com.shopsphere.orderservice.messaging;

import java.time.Instant;

/**
 * Standard wrapper of every event published by order-service. A consumer needs only these fields to route,
 * de-duplicate (eventId), order (aggregateId is also the Kafka key) and trace (correlationId) a message.
 * "eventVersion" is the version of the payload shape: new fields may be added within a version,
 * a breaking change gets a new version (and a new topic suffix).
 */
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producer,
        String aggregateType,
        String aggregateId,
        String correlationId,
        T payload) {
}
