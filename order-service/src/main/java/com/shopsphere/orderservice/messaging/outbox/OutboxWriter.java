package com.shopsphere.orderservice.messaging.outbox;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.orderservice.entity.OutboxEvent;
import com.shopsphere.orderservice.repository.OutboxEventRepository;

/** Queues a message for Kafka in the caller's database transaction (never publishes directly). */
@Component
public class OutboxWriter {

    private final OutboxEventRepository repository;

    public OutboxWriter(OutboxEventRepository repository) {
        this.repository = repository;
    }

    /** MANDATORY: enqueueing outside a transaction would break the atomicity the outbox exists for. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String topic, String messageKey, String eventType, String eventId,
                        Long aggregateId, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("ORDER");
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setTopic(topic);
        event.setMessageKey(messageKey);
        event.setEventId(eventId);
        event.setCorrelationId(MDC.get("requestId"));
        event.setPayload(payload);
        event.setStatus(OutboxEvent.PENDING);
        event.setAttempts(0);
        repository.save(event);
    }
}
