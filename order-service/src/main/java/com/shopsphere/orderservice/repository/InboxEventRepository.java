package com.shopsphere.orderservice.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopsphere.orderservice.entity.InboxEvent;

public interface InboxEventRepository extends JpaRepository<InboxEvent, Long> {

    boolean existsByEventIdAndConsumer(String eventId, String consumer);

    @Modifying
    @Query("delete from InboxEvent e where e.processedAt < :cutoff")
    int deleteProcessedBefore(@Param("cutoff") LocalDateTime cutoff);
}
