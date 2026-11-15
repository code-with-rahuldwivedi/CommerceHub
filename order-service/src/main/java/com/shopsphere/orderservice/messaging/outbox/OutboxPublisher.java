package com.shopsphere.orderservice.messaging.outbox;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.shopsphere.orderservice.config.OutboxProperties;

/**
 * Sends claimed outbox rows to Kafka. Delivery is AT LEAST ONCE: a crash between "sent" and "marked PUBLISHED"
 * re-sends the event, which is why every consumer must be idempotent (envelope events carry an eventId).
 */
@Component
@ConditionalOnProperty(prefix = "outbox.publisher", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxClaimService claimService;
    private final OutboxDeliveryService deliveryService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties properties;

    public OutboxPublisher(OutboxClaimService claimService, OutboxDeliveryService deliveryService,
                           KafkaTemplate<String, String> kafkaTemplate, OutboxProperties properties) {
        this.claimService = claimService;
        this.deliveryService = deliveryService;
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms}")
    public void publishPendingEvents() {
        List<ClaimedOutboxEvent> events;
        try {
            events = claimService.claimPendingEvents();
        } catch (RuntimeException e) {
            log.error("Could not claim outbox events", e);
            return;
        }

        for (ClaimedOutboxEvent event : events) {
            try {
                kafkaTemplate.send(toRecord(event))
                        .get(properties.publisher().sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
                deliveryService.markPublished(event.id());
            } catch (InterruptedException e) {
                // shutting down: leave the row PROCESSING, the stale-claim rule hands it to the next publisher
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException | RuntimeException e) {
                Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
                log.warn("Publishing outbox event {} ({}) to {} failed: {}",
                        event.id(), event.eventType(), event.topic(), cause.toString());
                deliveryService.markFailed(event.id(), cause.toString());
            }
        }
    }

    static ProducerRecord<String, String> toRecord(ClaimedOutboxEvent event) {
        ProducerRecord<String, String> record = new ProducerRecord<>(event.topic(), event.messageKey(), event.payload());
        addHeader(record, "event-id", event.eventId());
        addHeader(record, "event-type", event.eventType());
        addHeader(record, "correlation-id", event.correlationId());
        addHeader(record, "content-type", "application/json");
        return record;
    }

    private static void addHeader(ProducerRecord<String, String> record, String name, String value) {
        if (value != null) {
            record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
