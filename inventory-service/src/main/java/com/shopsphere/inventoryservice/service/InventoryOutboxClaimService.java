package com.shopsphere.inventoryservice.service;

import com.shopsphere.inventoryservice.entity.InventoryOutboxEvent;
import com.shopsphere.inventoryservice.repository.InventoryOutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InventoryOutboxClaimService {

    private final InventoryOutboxRepository repository;

    @Value("${inventory.outbox.stale-claim-seconds:120}")
    private long staleClaimSeconds;

    public InventoryOutboxClaimService(InventoryOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<ClaimedInventoryOutboxEvent> claimPendingEvents() {
        repository.recoverStaleClaims(LocalDateTime.now().minusSeconds(staleClaimSeconds));

        List<InventoryOutboxEvent> events = repository.claimPendingEvents();
        LocalDateTime now = LocalDateTime.now();
        for (InventoryOutboxEvent event : events) {
            event.setStatus("PROCESSING");
            event.setClaimedAt(now);
        }
        repository.saveAllAndFlush(events);

        return events.stream()
                .map(event -> new ClaimedInventoryOutboxEvent(
                        event.getId(), event.getOrderId(), event.getPayload(), event.getAttempts()))
                .toList();
    }
}
