package com.shopsphere.userservice.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Durable security audit trail. No FK to users on purpose: history outlives the account. */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "target_user_id")
    private Long targetUserId;

    @Column(length = 500)
    private String details;

    protected AuditEvent() {
    }

    public AuditEvent(LocalDateTime occurredAt, String action, Long actorUserId, Long targetUserId, String details) {
        this.occurredAt = occurredAt;
        this.action = action;
        this.actorUserId = actorUserId;
        this.targetUserId = targetUserId;
        this.details = details;
    }

    public Long getId() { return id; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public String getAction() { return action; }
    public Long getActorUserId() { return actorUserId; }
    public Long getTargetUserId() { return targetUserId; }
    public String getDetails() { return details; }
}
