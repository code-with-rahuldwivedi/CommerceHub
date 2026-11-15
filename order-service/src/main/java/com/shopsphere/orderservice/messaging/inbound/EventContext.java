package com.shopsphere.orderservice.messaging.inbound;

// Envelope metadata handed to a handler. 
public record EventContext(String eventId, String correlationId, String occurredAt) {
}
