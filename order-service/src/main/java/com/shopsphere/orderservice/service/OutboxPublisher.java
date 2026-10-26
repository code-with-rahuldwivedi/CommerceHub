package com.shopsphere.orderservice.service;

import com.shopsphere.orderservice.entity.OutboxEvent;
import com.shopsphere.orderservice.repository.OutboxEventRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${outbox.publisher.topic:order-events}")
    private String topic;

    public OutboxPublisher(
            OutboxEventRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    public void publishPendingEvents() {

        List<OutboxEvent> events =
                outboxRepository.findTop50ByStatusOrderByIdAsc("PENDING");

        for (OutboxEvent event : events) {
            try {
                kafkaTemplate.send(
                        topic,
                        String.valueOf(event.getAggregateId()),
                        event.getPayload()
                ).get(10, TimeUnit.SECONDS);

                event.setStatus("PUBLISHED");
                event.setPublishedAt(LocalDateTime.now());
                outboxRepository.save(event);

                log.info(
                        "Outbox event {} published to topic {}",
                        event.getId(), topic
                );

            } catch (Exception exception) {
                event.setAttempts(event.getAttempts() + 1);
                outboxRepository.save(event);

                log.error(
                        "Failed to publish outbox event {}. Attempt {}",
                        event.getId(), event.getAttempts(), exception
                );
            }
        }
    }
}
