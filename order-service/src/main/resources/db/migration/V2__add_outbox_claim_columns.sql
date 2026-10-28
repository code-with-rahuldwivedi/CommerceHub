
ALTER TABLE order_outbox_events
    ADD COLUMN claimed_at DATETIME(6) NULL,
    ADD COLUMN next_attempt_at DATETIME(6) NULL,
    ADD COLUMN last_error VARCHAR(2000) NULL;
