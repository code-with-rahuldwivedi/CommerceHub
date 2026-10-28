CREATE TABLE orders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    product_id BIGINT NULL,
    quantity INT NULL,
    total_price DECIMAL(19,2) NULL,
    status VARCHAR(255) NULL,
    order_date DATETIME(6) NULL,
    idempotency_key VARCHAR(100) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_orders_idempotency_key UNIQUE (idempotency_key),
    INDEX idx_orders_user_id (user_id)
) ENGINE=InnoDB;

CREATE TABLE order_outbox_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    attempts INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_order_outbox_status_id (status, id)
) ENGINE=InnoDB;
