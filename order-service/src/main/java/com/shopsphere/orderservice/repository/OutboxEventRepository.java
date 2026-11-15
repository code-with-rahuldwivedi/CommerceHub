package com.shopsphere.orderservice.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopsphere.orderservice.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Claims the next batch for publishing. Safe with several replicas (SKIP LOCKED), recovers events whose
     * claimant died (stale PROCESSING), and keeps events of one order and topic strictly in order: an event is only
     * eligible when no earlier event of the same aggregate and topic is still PENDING or PROCESSING.
     * Times are passed in (UTC) instead of using the database clock.
     */
    @Query(value = """
            SELECT e.*
            FROM order_outbox_events e
            WHERE (
                    (e.status = 'PENDING' AND (e.next_attempt_at IS NULL OR e.next_attempt_at <= :now))
                 OR (e.status = 'PROCESSING' AND e.claimed_at < :staleBefore)
                  )
              AND NOT EXISTS (
                    SELECT 1
                    FROM order_outbox_events p
                    WHERE p.aggregate_id = e.aggregate_id
                      AND p.topic = e.topic
                      AND p.id < e.id
                      AND p.status IN ('PENDING', 'PROCESSING')
                  )
            ORDER BY e.id
            LIMIT batchSize   //LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> claimPendingEvents(@Param("now") LocalDateTime now,
                                         @Param("staleBefore") LocalDateTime staleBefore,
                                         @Param("batchSize") int batchSize);

    long countByStatus(String status);

    @Query("select min(e.createdAt) from OutboxEvent e where e.status = 'PENDING'")
    LocalDateTime findOldestPendingCreatedAt();

    @Modifying
    @Query("delete from OutboxEvent e where e.status = 'PUBLISHED' and e.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") LocalDateTime cutoff);
}
