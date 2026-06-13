-- F-03: Add username + password auth fields; relax email to nullable.
-- Table is empty (F-02 shipped, zero registered users) so NOT NULL without DEFAULT is safe.
-- Email unique index intentionally kept (harmless; avoids H2/Postgres constraint-naming divergence).

ALTER TABLE users ADD COLUMN username VARCHAR(255) NOT NULL UNIQUE;
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255) NOT NULL;
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
