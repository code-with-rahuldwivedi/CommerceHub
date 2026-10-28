package com.shopsphere.orderservice.service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxClaimService claimService;
    private final OutboxDeliveryService deliveryService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${outbox.publisher.topic:order-events}")
    private String topic;

    public OutboxPublisher(
            OutboxClaimService claimService,
            OutboxDeliveryService deliveryService,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.claimService = claimService;
        this.deliveryService = deliveryService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:5000}")
    public void publishPendingEvents() {
        List<ClaimedOutboxEvent> events = claimService.claimPendingEvents();
        for (ClaimedOutboxEvent event : events) {
            try {
                kafkaTemplate.send(topic, String.valueOf(event.aggregateId()), event.payload())
                        .get(10, TimeUnit.SECONDS);
                deliveryService.markPublished(event.id());
                log.info("Outbox event {} published to topic {}", event.id(), topic);
            } catch (Exception exception) {
                try {
                    deliveryService.markFailed(event.id(), exception);
                } catch (RuntimeException persistenceException) {
                    // Lease expiry makes PROCESSING rows recoverable after this failure.
                    log.error("Could not record failed delivery for outbox event {}",
                            event.id(), persistenceException);
                }
                log.error("Failed to publish outbox event {} (attempt {})",
                        event.id(), event.attempts() + 1, exception);
            }
        }
    }
}
