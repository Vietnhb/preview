-- Schema retrieval vectors are derived data. Historical schema versions and
-- simulation snapshots remain untouched by this migration.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS schema_search_embeddings (
    schema_id VARCHAR(80) NOT NULL,
    schema_version VARCHAR(24) NOT NULL,
    topic VARCHAR(80) NOT NULL,
    search_text TEXT NOT NULL,
    embedding vector NOT NULL,
    embedding_provider VARCHAR(80) NOT NULL,
    embedding_model VARCHAR(160) NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    source_checksum VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_schema_search_embeddings PRIMARY KEY
        (schema_id, schema_version, embedding_provider, embedding_model),
    CONSTRAINT ck_schema_search_embeddings_dimension CHECK
        (embedding_dimension > 0 AND vector_dims(embedding) = embedding_dimension)
    -- Deliberately no FK to schema_versions: the legacy database bootstrap
    -- creates Hibernate-owned tables after Flyway. The index is derived data;
    -- routing joins it to the approved schema registry before returning hits.
);

CREATE INDEX IF NOT EXISTS idx_schema_search_embeddings_model
    ON schema_search_embeddings (embedding_provider, embedding_model, topic);
