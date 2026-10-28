package com.shopsphere.orderservice.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.orderservice.entity.OutboxEvent;
import com.shopsphere.orderservice.repository.OutboxEventRepository;

@Service
public class OutboxClaimService {

    private final OutboxEventRepository repository;

    @Value("${outbox.publisher.stale-claim-seconds:120}")
    private long staleClaimSeconds;

    public OutboxClaimService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<ClaimedOutboxEvent> claimPendingEvents() {

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime staleBefore = now.minusSeconds(staleClaimSeconds);

        List<OutboxEvent> events =
                repository.claimPendingEvents(staleBefore);

        for (OutboxEvent event : events) {
            event.setStatus("PROCESSING");
            event.setClaimedAt(now);
        }

        repository.saveAllAndFlush(events);

        return events.stream()
                .map(event -> new ClaimedOutboxEvent(
                        event.getId(),
                        event.getAggregateId(),
                        event.getPayload(),
                        event.getAttempts()))
                .toList();
    }
}
