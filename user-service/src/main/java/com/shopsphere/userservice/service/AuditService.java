package com.shopsphere.userservice.service;

import java.time.Clock;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopsphere.userservice.entity.AuditAction;
import com.shopsphere.userservice.entity.AuditEvent;
import com.shopsphere.userservice.repository.AuditEventRepository;

/**
 * Durable audit trail for security-relevant events (also mirrored to the AUDIT log).
 * High-volume events (every login attempt) are only logged, not stored.
 */
@Service
public class AuditService {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");
    private static final int MAX_DETAILS = 500;

    private final AuditEventRepository repository;
    private final Clock clock;

    public AuditService(AuditEventRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void record(AuditAction action, Long actorUserId, Long targetUserId, String details) {
        String safeDetails = details == null ? null
                : details.length() > MAX_DETAILS ? details.substring(0, MAX_DETAILS) : details;
        repository.save(new AuditEvent(LocalDateTime.now(clock), action.name(), actorUserId, targetUserId, safeDetails));
        AUDIT.info("audit action={} actor={} target={} details={}", action, actorUserId, targetUserId, safeDetails);
    }

    @Transactional
    public int purgeOlderThanDays(int days) {
        return repository.deleteOlderThan(LocalDateTime.now(clock).minusDays(days));
    }
}
