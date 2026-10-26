package com.shopsphere.inventoryservice.service;

import com.shopsphere.inventoryservice.entity.InventoryOutboxEvent;
import com.shopsphere.inventoryservice.repository.InventoryOutboxRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class InventoryOutboxPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(InventoryOutboxPublisher.class);

    private final InventoryOutboxClaimService claimService;
    private final InventoryOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${inventory.kafka.result-topic:inventory-events}")
    private String topic;

    public InventoryOutboxPublisher(
            InventoryOutboxClaimService claimService,
            InventoryOutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate) {

        this.claimService = claimService;
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 30000)
    public void publishPendingEvents() {

        List<ClaimedInventoryOutboxEvent> events =
                claimService.claimPendingEvents();

        for (ClaimedInventoryOutboxEvent event : events) {
            try {
                kafkaTemplate.send(
                        topic,
                        String.valueOf(event.orderId()),
                        event.payload()
                ).get(10, TimeUnit.SECONDS);

                markPublished(event.id());

                log.info(
                        "Inventory outbox event {} published to {}",
                        event.id(),
                        topic
                );

            } catch (Exception exception) {
                markFailed(event.id(), exception);

                log.error(
                        "Failed to publish inventory outbox event {}",
                        event.id(),
                        exception
                );
            }
        }
    }

    @Transactional
    public void markPublished(Long eventId) {

        InventoryOutboxEvent event = outboxRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Outbox event not found: " + eventId));

        event.setStatus("PUBLISHED");
        event.setPublishedAt(LocalDateTime.now());
        event.setClaimedAt(null);
        event.setLastError(null);

        outboxRepository.save(event);
    }

    @Transactional
    public void markFailed(Long eventId, Exception exception) {

        InventoryOutboxEvent event = outboxRepository.findById(eventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Outbox event not found: " + eventId));

        event.setAttempts(event.getAttempts() + 1);
        event.setClaimedAt(null);

        String error = exception.getMessage();

        if (error == null || error.isBlank()) {
            error = exception.getClass().getSimpleName();
        }

        if (error.length() > 2000) {
            error = error.substring(0, 2000);
        }

        event.setLastError(error);

        // Return to PENDING so the scheduler can retry.
        event.setStatus("PENDING");

        outboxRepository.save(event);
    }
}
