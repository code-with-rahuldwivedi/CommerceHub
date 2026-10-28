package com.shopsphere.orderservice.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.orderservice.entity.OutboxEvent;
import com.shopsphere.orderservice.repository.OutboxEventRepository;

@Service
public class OutboxDeliveryService {
    private final OutboxEventRepository repository;

    public OutboxDeliveryService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void markPublished(Long eventId) {
        OutboxEvent event = repository.findById(eventId).orElseThrow(() ->
                new IllegalStateException("Outbox event not found: " + eventId));
        event.setStatus("PUBLISHED");
        event.setPublishedAt(LocalDateTime.now());
        event.setClaimedAt(null);
        event.setNextAttemptAt(null);
        event.setLastError(null);
        repository.save(event);
    }

    @Transactional
    public void markFailed(Long eventId, Exception exception) {
        OutboxEvent event = repository.findById(eventId).orElseThrow(() ->
                new IllegalStateException("Outbox event not found: " + eventId));
        int attempts = event.getAttempts() == null ? 1 : event.getAttempts() + 1;
        event.setAttempts(attempts);
        event.setClaimedAt(null);
        event.setStatus("PENDING");
        long delaySeconds = Math.min(300L, 5L * (1L << Math.min(attempts - 1, 6)));
        event.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));
        String message = exception.getMessage();
        if (message == null || message.isBlank()) message = exception.getClass().getSimpleName();
        event.setLastError(message.length() > 2000 ? message.substring(0, 2000) : message);
        repository.save(event);
    }
}
