-- Soft delete / visibility, optimistic locking, audit columns, DB-level price guard, search indexes.
ALTER TABLE products
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_by BIGINT NULL,
    MODIFY created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    MODIFY updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

-- Fails the migration (on purpose) if legacy rows have a non-positive price: fix the data first.
ALTER TABLE products ADD CONSTRAINT chk_products_price_positive CHECK (price > 0);

CREATE INDEX idx_products_status_category_price ON products (status, category, price);
CREATE INDEX idx_products_status_price ON products (status, price);
CREATE INDEX idx_products_status_created ON products (status, created_at);
CREATE INDEX idx_products_name ON products (name);
