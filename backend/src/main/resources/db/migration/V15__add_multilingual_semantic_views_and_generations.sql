-- Versioned multilingual retrieval metadata is derived data. This migration
-- adds only new relations; schema, solver, run, assignment, and old embedding
-- history are preserved.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS schema_search_index_generations (
    generation_id UUID PRIMARY KEY,
    projection_version VARCHAR(40) NOT NULL,
    embedding_provider VARCHAR(80) NOT NULL,
    embedding_model VARCHAR(160) NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    lifecycle_state VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    activated_at TIMESTAMPTZ,
    CONSTRAINT ck_schema_search_generation_dimension CHECK (embedding_dimension > 0),
    CONSTRAINT ck_schema_search_generation_state CHECK
        (lifecycle_state IN ('BUILDING', 'READY', 'ACTIVE', 'RETIRED', 'FAILED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_schema_search_one_active_generation
    ON schema_search_index_generations (projection_version, embedding_provider, embedding_model, embedding_dimension)
    WHERE lifecycle_state = 'ACTIVE';

CREATE TABLE IF NOT EXISTS schema_search_embedding_views (
    generation_id UUID NOT NULL,
    schema_id VARCHAR(80) NOT NULL,
    schema_version VARCHAR(24) NOT NULL,
    topic VARCHAR(80) NOT NULL,
    locale VARCHAR(8) NOT NULL,
    semantic_view_type VARCHAR(64) NOT NULL,
    semantic_view_version VARCHAR(40) NOT NULL,
    semantic_text TEXT NOT NULL,
    embedding vector NOT NULL,
    embedding_provider VARCHAR(80) NOT NULL,
    embedding_model VARCHAR(160) NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    projection_version VARCHAR(40) NOT NULL,
    source_checksum VARCHAR(64) NOT NULL,
    lifecycle_state VARCHAR(16) NOT NULL DEFAULT 'APPROVED',
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_schema_search_embedding_views PRIMARY KEY
        (generation_id, schema_id, schema_version, locale, semantic_view_type, semantic_view_version,
         embedding_provider, embedding_model),
    CONSTRAINT ck_schema_search_embedding_views_dimension CHECK
        (embedding_dimension > 0 AND vector_dims(embedding) = embedding_dimension),
    CONSTRAINT ck_schema_search_embedding_views_locale CHECK (locale IN ('en', 'vi')),
    CONSTRAINT ck_schema_search_embedding_views_state CHECK
        (lifecycle_state IN ('APPROVED', 'RETIRED'))
);

CREATE INDEX IF NOT EXISTS idx_schema_search_embedding_views_active
    ON schema_search_embedding_views
        (generation_id, embedding_provider, embedding_model, embedding_dimension, schema_id, schema_version);

CREATE INDEX IF NOT EXISTS idx_schema_search_embedding_views_identity
    ON schema_search_embedding_views (schema_id, schema_version, locale, semantic_view_type);
