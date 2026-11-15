-- The outbox can now carry events for several topics (versioned stream + legacy stream).
ALTER TABLE order_outbox_events
    ADD COLUMN topic VARCHAR(200) NOT NULL DEFAULT 'order-events',
    ADD COLUMN message_key VARCHAR(100) NULL,
    ADD COLUMN event_id VARCHAR(36) NULL,
    ADD COLUMN correlation_id VARCHAR(64) NULL;

UPDATE order_outbox_events SET message_key = CAST(aggregate_id AS CHAR) WHERE message_key IS NULL;

CREATE INDEX idx_outbox_claim ON order_outbox_events (status, next_attempt_at, id);
CREATE INDEX idx_outbox_aggregate ON order_outbox_events (aggregate_id, topic, status, id);
