CREATE TABLE inventory_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    available_quantity INT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventory_product_id UNIQUE (product_id),
    INDEX idx_inventory_product_id (product_id)
) ENGINE=InnoDB;

CREATE TABLE inventory_operations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    operation_key VARCHAR(150) NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    operation_type VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventory_operation_key UNIQUE (operation_key)
) ENGINE=InnoDB;

CREATE TABLE inventory_outbox_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_inventory_outbox_order_event UNIQUE (order_id, event_type),
    INDEX idx_inventory_outbox_status_attempt (status, id)
) ENGINE=InnoDB;
