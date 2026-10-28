SET @shopsphere_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_outbox_events' AND COLUMN_NAME = 'claimed_at');
SET @shopsphere_sql = IF(@shopsphere_exists = 0, 'ALTER TABLE inventory_outbox_events ADD COLUMN claimed_at DATETIME(6) NULL', 'SELECT 1');
PREPARE shopsphere_stmt FROM @shopsphere_sql;
EXECUTE shopsphere_stmt;
DEALLOCATE PREPARE shopsphere_stmt;

SET @shopsphere_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_outbox_events' AND COLUMN_NAME = 'last_error');
SET @shopsphere_sql = IF(@shopsphere_exists = 0, 'ALTER TABLE inventory_outbox_events ADD COLUMN last_error VARCHAR(2000) NULL', 'SELECT 1');
PREPARE shopsphere_stmt FROM @shopsphere_sql;
EXECUTE shopsphere_stmt;
DEALLOCATE PREPARE shopsphere_stmt;

SET @shopsphere_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_outbox_events' AND COLUMN_NAME = 'next_attempt_at');
SET @shopsphere_sql = IF(@shopsphere_exists = 0, 'ALTER TABLE inventory_outbox_events ADD COLUMN next_attempt_at DATETIME(6) NULL', 'SELECT 1');
PREPARE shopsphere_stmt FROM @shopsphere_sql;
EXECUTE shopsphere_stmt;
DEALLOCATE PREPARE shopsphere_stmt;
