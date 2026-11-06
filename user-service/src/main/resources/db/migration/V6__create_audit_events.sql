CREATE TABLE audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    occurred_at DATETIME(6) NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor_user_id BIGINT NULL,
    target_user_id BIGINT NULL,
    details VARCHAR(500) NULL,
    PRIMARY KEY (id),
    INDEX idx_audit_target (target_user_id, occurred_at),
    INDEX idx_audit_occurred (occurred_at)
) ENGINE=InnoDB;
