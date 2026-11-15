ALTER TABLE orders
    ADD COLUMN subtotal_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN shipping_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN tax_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN discount_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    ADD COLUMN updated_at DATETIME(6) NULL,
    ADD COLUMN request_hash VARCHAR(64) NULL,
    ADD COLUMN cancellation_reason VARCHAR(255) NULL,
    ADD COLUMN contact_email VARCHAR(254) NULL,
    ADD COLUMN ship_full_name VARCHAR(100) NULL,
    ADD COLUMN ship_line1 VARCHAR(200) NULL,
    ADD COLUMN ship_line2 VARCHAR(200) NULL,
    ADD COLUMN ship_city VARCHAR(100) NULL,
    ADD COLUMN ship_state VARCHAR(100) NULL,
    ADD COLUMN ship_postal_code VARCHAR(20) NULL,
    ADD COLUMN ship_country VARCHAR(2) NULL,
    ADD COLUMN ship_phone VARCHAR(20) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Existing orders were single-product orders with the total stored on the order.
UPDATE orders
   SET subtotal_amount = COALESCE(total_price, 0.00),
       updated_at = COALESCE(order_date, CURRENT_TIMESTAMP(6));

ALTER TABLE orders MODIFY updated_at DATETIME(6) NOT NULL;

INSERT INTO order_items (order_id, product_id, product_name, unit_price, quantity, line_total)
SELECT o.id,
       o.product_id,
       CONCAT('Product #', o.product_id),
       ROUND(COALESCE(o.total_price, 0.00) / o.quantity, 2),
       o.quantity,
       COALESCE(o.total_price, 0.00)
  FROM orders o
 WHERE o.product_id IS NOT NULL AND o.quantity IS NOT NULL AND o.quantity > 0;

INSERT INTO order_status_history (order_id, from_status, to_status, reason, actor, created_at)
SELECT o.id, NULL, COALESCE(o.status, 'PENDING'), 'migrated from order-service v1', 'system:migration',
       COALESCE(o.updated_at, CURRENT_TIMESTAMP(6))
  FROM orders o;

-- Idempotency keys are scoped per user: one customer's key must not collide with (or reveal) another's.
ALTER TABLE orders DROP INDEX uk_orders_idempotency_key;
ALTER TABLE orders ADD CONSTRAINT uk_orders_user_idempotency UNIQUE (user_id, idempotency_key);

CREATE INDEX idx_orders_user_date ON orders (user_id, order_date);
CREATE INDEX idx_orders_status_updated ON orders (status, updated_at);
