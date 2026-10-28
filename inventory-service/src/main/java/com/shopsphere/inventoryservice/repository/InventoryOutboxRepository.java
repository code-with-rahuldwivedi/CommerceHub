package com.shopsphere.inventoryservice.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopsphere.inventoryservice.entity.InventoryOutboxEvent;

public interface InventoryOutboxRepository
        extends JpaRepository<InventoryOutboxEvent, Long> {

    @Query(value = """
            SELECT *
            FROM inventory_outbox_events
            WHERE status = 'PENDING'
              AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP)
            ORDER BY id
            LIMIT 50
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<InventoryOutboxEvent> claimPendingEvents();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE inventory_outbox_events
               SET status = 'PENDING',
                   claimed_at = NULL,
                   next_attempt_at = CURRENT_TIMESTAMP,
                   last_error = CONCAT('Recovered stale PROCESSING claim at ', CURRENT_TIMESTAMP)
             WHERE status = 'PROCESSING'
               AND claimed_at IS NOT NULL
               AND claimed_at < :staleBefore
            """, nativeQuery = true)
    int recoverStaleClaims(@Param("staleBefore") LocalDateTime staleBefore);

    boolean existsByOrderIdAndEventType(Long orderId, String eventType);
}
