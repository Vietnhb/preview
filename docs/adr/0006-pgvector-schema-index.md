# PostgreSQL pgvector index for schema routing

Status: Accepted; migration verification is incomplete

## Context

Hybrid routing needs vector similarity over a small set of approved schema versions. The backend already uses PostgreSQL and Flyway. Adding a separate vector database would add another operational dependency without evidence that the current corpus requires it.

## Decision

Store schema search embeddings in the existing PostgreSQL instance with pgvector. Flyway migration `V8__add_hybrid_schema_search.sql` enables the `vector` extension and creates `schema_search_embeddings`, keyed by schema ID, schema version, embedding provider, and embedding model. Rows also retain topic, projected search text, configured dimension, source definition checksum, and timestamps. V9 adds a nullable solver-binding checksum for verified catalog backfill.

The vector column uses pgvector’s unconstrained `vector` type. The migration checks that the declared `embedding_dimension` is positive and equals `vector_dims(embedding)`. Application configuration pins provider, model, dimension, and timeout; both provider responses and query vectors are checked for the configured dimension and finite numeric values. No truncation or padding is performed.

V8 intentionally does not declare a foreign key to `schema_versions`, because this repository runs Flyway before Hibernate creates its application tables. The derived vector index is joined against approved schema rows and enabled topics at query time. V9 adds a nullable checksum for the solver binding; bootstrap records it only after checking the stored binding against the source catalog.

The store uses parameterized JDBC for idempotent upsert and exact cosine retrieval (`<=>`). Retrieval filters on provider/model/dimension and joins current schema versions and enabled topics, requiring an approved lifecycle and matching non-null definition checksum. Ordering is deterministic by cosine distance, schema ID, then version.

Do not create an HNSW index at the current catalog scale. Exact cosine search is easier to verify and keeps the migration simple; reconsider ANN only after representative corpus/query benchmarks show that exact search misses configured latency targets.

The table contains derived retrieval data only. The migration does not rewrite or delete schema history, simulation runs, or assignment snapshots. Retired versions may retain their vectors for historical traceability, but the route query excludes them.

## Consequences

- PostgreSQL remains the only database service; provider/model identity is part of the primary key so vectors for different embedding spaces cannot be confused.
- The vector extension is a readiness requirement when routing is enabled. Missing extension/index readiness is reported as a health failure, and routing does not silently substitute a fake or random result.
- The database dimension constraint verifies self-consistency of each stored vector row; the application also verifies that row/query dimensions match active model configuration.
- Search remains exact with deterministic ordering. HNSW and its tuning/maintenance burden are deferred until a benchmark justifies them.

## Implementation evidence

Migration: `backend/src/main/resources/db/migration/V8__add_hybrid_schema_search.sql` and `V9__add_solver_binding_checksum.sql`. JDBC and cosine retrieval: `backend/src/main/java/com/example/backend/schema/routing/vector/PgVectorSchemaEmbeddingStore.java` and `PgVectorSchemaRetriever.java`. Readiness: `SchemaRoutingHealthIndicator.java`.

## Known gaps and migration blocker

- Testcontainers coverage now verifies V8/V9, pgvector storage, the application cosine query, projection freshness, and a preserved prior-schema fixture from a V7 baseline. The two tests were skipped locally because Docker Desktop was unavailable.
- The full V1–V9 chain still cannot run on a truly empty database: V4 first alters the Hibernate-owned `assignment_submissions` table without an existence guard; V5–V7 also expect Hibernate-created tables. V8 no longer depends on `schema_versions` existing before Hibernate. Resolve the legacy bootstrap sequencing without changing applied migrations, then prove the full clean chain.
- The embedding freshness check compares `search_text` as well as schema checksum/provider/model/dimension, so projection changes trigger re-embedding. A formal projection-format version may still help with lifecycle observability.
- The indexer upserts current approved documents but does not prune stale or retired embedding rows. They are excluded from new routes by lifecycle/topic filters; a bounded retention policy remains an operational decision.
- Dimension is checked per row rather than encoded as a fixed `vector(n)` typmod because dimension comes from typed provider configuration. The active identity and database check must remain aligned.

This migration and routing path are not production ready until the full clean-database migration chain is resolved, the PostgreSQL/pgvector tests run successfully in a Docker-enabled environment, and the other required production gates are closed.
