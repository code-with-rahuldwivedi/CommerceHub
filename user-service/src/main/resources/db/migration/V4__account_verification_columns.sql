ALTER TABLE users
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN password_changed_at DATETIME(6) NULL;

-- Accounts that existed before verification was introduced are treated as verified.
UPDATE users SET email_verified = TRUE;
