# Hybrid schema routing and candidate-bounded extraction

Status: Accepted; implementation is incomplete

## Context

The extraction provider previously built its prompt from the approved catalog. That made prompt size grow with every catalog addition and allowed the model to choose among schemas without a retrieval decision pinned by the backend. Schema routing must use current approved data, remain generic as schema versions are added, and leave final membership and physical-contract decisions to the backend.

## Decision

Use two complementary retrievers over approved, enabled schema versions:

1. Build a deterministic `SchemaSearchDocument` from the version identity and approved metadata: topic, name, model ID, description/curriculum labels, quantity keys, aliases, symbols, units, relations, and end-condition capabilities. Search text is normalized with Unicode NFKC. Formulas, solver/reference IDs, outputs, visualization assets, and user data are excluded.
2. Score the complete in-memory document snapshot with BM25 (`Bm25SchemaRetriever`). The index stores term frequencies, document frequencies, document lengths, and average length; the query path reuses the immutable snapshot. The Unicode tokenizer is schema agnostic and retains word, number, canonical-key, symbol, and unit tokens.
3. Embed the query and retrieve matching schema-version embeddings from PostgreSQL/pgvector using cosine distance.
4. Fuse the lexical and vector rankings with Reciprocal Rank Fusion. Retriever scores are not added across their incompatible scales. Ties are resolved by schema ID and version.
5. Verify fused candidates using generic evidence from required-quantity coverage, known-unit compatibility, and metadata overlap. Validated configuration supplies top-K limits, RRF `k`, thresholds, and evidence weights. If minimum evidence, score, or top-candidate margin is not met, return an explicit ambiguous decision.
6. Pin candidate schema IDs and versions in the route decision. `ExtractionPromptBuilder` projects only those candidates’ extraction contracts into the prompt. Strict response shape and exact candidate membership are checked before Jackson binding. Retry reuses the same candidate set; ambiguity resolution reloads the already pinned schema version.

An approved schema addition is data, not a routing rule: the indexer reads approved versions and the routing implementation contains no per-schema or per-topic selection branches. The current indexer rebuilds the snapshot at application startup and provides a catalog-change refresh method.

## Consequences

- AI prompt content is bounded by configured candidate count and total character limit instead of containing the full catalog.
- New approved metadata can participate without editing the central router, and low-evidence requests can be surfaced for clarification rather than silently selecting a schema.
- Routing depends on both the embedding provider and PostgreSQL’s vector extension. Unavailable routing fails the request/readiness path; there is no silent lexical-only production fallback.
- BM25 and exact cosine retrieval are deliberately simple for the current catalog scale. The current implementation uses an in-memory lexical snapshot and stores vectors in the existing PostgreSQL database.

## Implementation evidence

Primary code is under `backend/src/main/java/com/example/backend/schema/routing/` and `backend/src/main/java/com/example/backend/ai/extraction/prompt/`. Focused tests currently cover BM25, RRF, route decisions, prompt projection, strict JSON parsing, and candidate membership in the corresponding `backend/src/test/java/` packages.

## Known gaps

- This ADR records the intended routing boundary; it does not assert production readiness. A Testcontainers PostgreSQL/pgvector test now covers the store and V8/V9 migrations from a V7 baseline, but both tests were skipped locally because Docker was unavailable. The legacy V1–V7 chain still cannot start from a truly empty database.
- `OpenRouterEmbeddingClient` is the only production embedding implementation. There is no bounded retry/circuit-breaker policy or query-embedding cache.
- Startup indexing may make one external embedding request for each schema version needing an embedding. A standalone dry-run/reindex administration command and automatic catalog-publish trigger are not yet wired; callers must invoke the refresh method after an in-process catalog change.
- The contract verifier is a configurable lexical/metadata heuristic, not a calibrated probability model. Its thresholds need evaluation against a labeled curriculum query set before being treated as a reliable confidence estimate.
- The search-document builder currently projects fields directly from the stored schema definition. Comprehensive compiler-level validation of every search field, unit, alias, and relation remains part of the wider schema compiler work.
- The embedding freshness check also compares stored search text with the newly projected document, so projection changes trigger re-embedding when the schema-definition checksum is unchanged. A formal projection-format version may still help with lifecycle observability.

Until these gaps and the required migration, runtime, and end-to-end gates are closed, the backend must not be described as production ready.
