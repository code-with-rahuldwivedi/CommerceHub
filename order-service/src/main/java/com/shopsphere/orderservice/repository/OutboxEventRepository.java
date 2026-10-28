
package com.shopsphere.orderservice.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopsphere.orderservice.entity.OutboxEvent;

public interface OutboxEventRepository
        extends JpaRepository<OutboxEvent, Long> {

    @Query(value = """
            SELECT *
            FROM order_outbox_events
            WHERE (
                status = 'PENDING'
                AND (
                    next_attempt_at IS NULL
                    OR next_attempt_at <= CURRENT_TIMESTAMP
                )
            )
            OR (
                status = 'PROCESSING'
                AND claimed_at < :staleBefore
            )
            ORDER BY id
            LIMIT 50
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> claimPendingEvents(
            @Param("staleBefore") LocalDateTime staleBefore);
}
	