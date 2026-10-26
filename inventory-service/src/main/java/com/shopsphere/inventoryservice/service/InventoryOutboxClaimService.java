package com.shopsphere.inventoryservice.service;

import com.shopsphere.inventoryservice.entity.InventoryOutboxEvent;
import com.shopsphere.inventoryservice.repository.InventoryOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InventoryOutboxClaimService {

    private final InventoryOutboxRepository repository;

    public InventoryOutboxClaimService(
            InventoryOutboxRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public List<ClaimedInventoryOutboxEvent> claimPendingEvents() {

        List<InventoryOutboxEvent> events =
                repository.claimPendingEvents();

        LocalDateTime now = LocalDateTime.now();

        for (InventoryOutboxEvent event : events) {
            event.setStatus("PROCESSING");
            event.setClaimedAt(now);
        }

        repository.saveAllAndFlush(events);

        return events.stream()
                .map(event -> new ClaimedInventoryOutboxEvent(
                        event.getId(),
                        event.getOrderId(),
                        event.getPayload(),
                        event.getAttempts()
                ))
                .toList();
    }
}
