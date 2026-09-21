-- Add optimistic-lock columns in a new migration. V21 is already immutable.
ALTER TABLE IF EXISTS solver_versions
    ADD COLUMN IF NOT EXISTS record_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE IF EXISTS schema_versions
    ADD COLUMN IF NOT EXISTS record_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE IF EXISTS library_items
    ADD COLUMN IF NOT EXISTS record_version BIGINT NOT NULL DEFAULT 0;
