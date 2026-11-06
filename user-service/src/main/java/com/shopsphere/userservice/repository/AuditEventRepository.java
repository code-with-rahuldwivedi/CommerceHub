package com.shopsphere.userservice.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopsphere.userservice.entity.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    @Modifying
    @Query("delete from AuditEvent e where e.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
