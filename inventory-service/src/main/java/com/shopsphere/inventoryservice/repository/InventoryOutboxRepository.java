package com.shopsphere.inventoryservice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.shopsphere.inventoryservice.entity.InventoryOutboxEvent;

public interface InventoryOutboxRepository
        extends JpaRepository<InventoryOutboxEvent, Long> {

    @Query(value = """
            SELECT *
            FROM inventory_outbox_events
            WHERE status = 'PENDING'
              AND (next_attempt_at IS NULL
                   OR next_attempt_at <= CURRENT_TIMESTAMP)
            ORDER BY id
            LIMIT 50
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<InventoryOutboxEvent> claimPendingEvents();

    boolean existsByOrderIdAndEventType(
            Long orderId,
            String eventType
    );
}
