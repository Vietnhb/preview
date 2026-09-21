-- Search projection identity is derived metadata. Existing embeddings are
-- explicitly versioned as v1; a future projection change must reindex rather
-- than consume vectors built from a different projection contract.
ALTER TABLE schema_search_embeddings
    ADD COLUMN IF NOT EXISTS projection_version VARCHAR(40) NOT NULL DEFAULT 'v1';

ALTER TABLE schema_search_embeddings
    DROP CONSTRAINT IF EXISTS ck_schema_search_embeddings_projection_version;

ALTER TABLE schema_search_embeddings
    ADD CONSTRAINT ck_schema_search_embeddings_projection_version
    CHECK (projection_version <> '');
