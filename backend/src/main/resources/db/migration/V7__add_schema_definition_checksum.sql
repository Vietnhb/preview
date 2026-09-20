ALTER TABLE schema_versions ADD COLUMN IF NOT EXISTS definition_checksum VARCHAR(64);
