package com.shopsphere.orderservice.messaging.inbound;

import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Plug-in point for a service that talks BACK to the order service (inventory, payment, shipping, ...).
 * To integrate a new service: implement this interface as a @Component, list its eventTypes, and add its
 * topic to "orders.inbound.topics". The dispatcher takes care of parsing, de-duplication (inbox), metrics,
 * retries and the dead-letter topic. No existing class has to change.
 * <p>
 * Contract: handle() runs inside the dispatcher's transaction. Throw {@code NonRetryableEventException} for
 * messages that can never succeed (they go straight to the DLT); any other exception is retried.
 */
public interface InboundEventHandler {

    /** Stable name; part of the de-duplication key and of metric tags. */
    String name();

    /** Event types this handler owns. Two handlers must never claim the same type. */
    Set<String> eventTypes();

    void handle(String eventType, JsonNode payload, EventContext context);
}
