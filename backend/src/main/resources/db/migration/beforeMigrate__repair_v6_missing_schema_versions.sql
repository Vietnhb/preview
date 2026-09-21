-- Repair one legacy bootstrap state before V7 runs.
--
-- Some databases were allowed to record Flyway V1-V6 after Hibernate had
-- created only part of the entity schema. V1 and V3 deliberately skipped
-- schema_versions when it was absent, while V7 assumes that table exists.
-- This callback is intentionally narrower than a general schema bootstrap:
-- it acts only when the latest successful version is exactly 6 and the table
-- is absent. Existing tables and every database at another version are left
-- untouched. V7 remains the owner of definition_checksum.
DO $$
DECLARE
    current_flyway_version VARCHAR(50);
BEGIN
    IF to_regclass('flyway_schema_history') IS NULL THEN
        RETURN;
    END IF;

    SELECT version
      INTO current_flyway_version
      FROM flyway_schema_history
     WHERE success
       AND version IS NOT NULL
     ORDER BY installed_rank DESC
     LIMIT 1;

    IF current_flyway_version = '6'
       AND to_regclass('schema_versions') IS NULL THEN
        CREATE TABLE schema_versions (
            id UUID NOT NULL,
            created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
            updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
            schema_id VARCHAR(80) NOT NULL,
            name VARCHAR(120) NOT NULL,
            topic VARCHAR(80) NOT NULL,
            version VARCHAR(24) NOT NULL,
            definition JSONB NOT NULL,
            lifecycle_status VARCHAR(16) NOT NULL,
            CONSTRAINT schema_versions_pkey PRIMARY KEY (id),
            CONSTRAINT ck_schema_versions_lifecycle_status CHECK (
                lifecycle_status IN ('DRAFT', 'APPROVED', 'PUBLISHED', 'DEPRECATED', 'RETIRED')
            )
        );

        CREATE UNIQUE INDEX uk_schema_versions_identity
            ON schema_versions (schema_id, version);
        CREATE INDEX idx_schema_versions_lifecycle
            ON schema_versions (lifecycle_status, topic, schema_id);
    END IF;
END $$;
