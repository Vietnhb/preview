-- Solver bindings are immutable per schema version. The bootstrap fills this
-- checksum only after confirming that a stored binding matches the source.
ALTER TABLE IF EXISTS solver_versions
    ADD COLUMN IF NOT EXISTS binding_checksum VARCHAR(64);
