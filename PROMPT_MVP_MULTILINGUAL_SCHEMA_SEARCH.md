# PhysLive MVP: Production-Grade Multilingual Schema Retrieval

## 1. Mission

Act as the implementation owner for the PhysLive schema-search engine. Complete a production-grade multilingual retrieval slice for exactly these curriculum topics:

- CIRCUITS
- DYNAMICS
- KINEMATICS

Implement the work continuously in the repository. Do not stop at a proposal, prototype, sample query, or passing build. Do not expand the MVP to other topics. Existing data and behavior for other topics must remain intact.

The goal is to find a small, trustworthy set of schema candidates from Vietnamese or English problem text without sending the full catalog to the extraction AI. The extraction AI may receive only three to five pinned candidate contracts. Low-confidence input must produce an explicit ambiguity result.

This prompt is subordinate to the repository safety, persistence, physics-runtime, and compatibility requirements in PROMPT_PRODUCTION_COMPLETION.md. If the documents conflict, apply the stricter data-safety and verification rule.

## 2. Current problem to reproduce

The current retrieval projection is dominated by English technical identifiers. Natural Vietnamese queries have poor lexical recall, and natural English queries can also rank unrelated schemas above the intended schema.

Reproduce and record the current behavior before changing it. Include at least these MVP smoke cases, plus independently written paraphrases:

| Language | Problem intent | Expected schema family |
|---|---|---|
| Vietnamese | Tụ điện phóng điện qua điện trở | circuits_rc_discharging |
| Vietnamese | Lò xo có độ cứng k bị kéo dãn và tác dụng lực đàn hồi | hooke_law |
| Vietnamese | Một vật được ném xiên với vận tốc ban đầu và góc ném | kinematics_projectile |
| English | A capacitor discharges through a resistor | circuits_rc_discharging |
| English | A stretched spring produces a restoring force | hooke_law |
| English | A body is launched at an angle with an initial velocity | kinematics_projectile |

These examples are evaluation fixtures, never router branches or benchmark-specific synonyms in Java code.

## 3. Non-negotiable safety boundary

1. Do not run development startup, migration experiments, reindex, tests, or bootstrap against the configured Supabase database.
2. Use a local disposable PostgreSQL database with pgvector for integration work. Confirm the host and database name before every migration or reindex command.
3. Do not issue DROP, TRUNCATE, broad DELETE, CREATE DATABASE, or CREATE SCHEMA against shared infrastructure.
4. Do not edit an applied migration. Add the next incremental migration only after inspecting Flyway history.
5. Preserve every published schema version, solver binding, run snapshot, assignment snapshot, and historical embedding record required for audit or replay.
6. Do not write generated search text or vectors directly through the Supabase dashboard. All index rows must be reproducibly generated from authoritative source data.
7. Never log raw problem text, prompts, images, secrets, full vectors, or provider responses.

## 4. Required retrieval architecture

Implement this bounded flow:

    Problem or OCR text
      -> Unicode and OCR-safe normalization
      -> lexical retrieval over technical and multilingual lexical text
      -> multilingual semantic retrieval over precomputed semantic views
      -> deterministic Reciprocal Rank Fusion
      -> generic contract evidence and optional measured reranking
      -> confidence and ambiguity policy
      -> three to five pinned candidate contracts
      -> extraction AI
      -> strict candidate membership and JSON validation

The backend remains the authority. Retrieval proposes candidates; it does not approve schemas or physical correctness.

## 5. Search document version 2

Create one deterministic, versioned projection for each latest approved schema in the three MVP topics. Separate the following concerns instead of concatenating everything into one noisy field.

### 5.1 Lexical view

The lexical view may contain:

- Schema name and stable schema identity.
- Vietnamese and English names.
- Canonical quantity keys.
- Reviewed Vietnamese and English aliases.
- Physical symbols with case preserved.
- Accepted units.
- Curriculum labels and learning outcomes.
- Generic relation and end-condition labels.

Use this view for BM25. Preserve the original accented Vietnamese tokens and add an accent-folded shadow form for retrieval only. Do not replace canonical text with the folded form.

### 5.2 Semantic views

Create natural-language semantic views from authoritative metadata. At minimum, support Vietnamese and English views for every MVP schema. Each view should describe:

- The physical situation or phenomenon.
- The quantities normally given in the problem.
- The quantity or behavior being investigated.
- Common curriculum wording and reviewed paraphrases.
- Close concepts that distinguish the schema from neighboring schemas, expressed as positive metadata rather than exclusion rules.

Do not embed formulas, solver implementation, reference implementation, visualization configuration, sample answers, private user data, or generated code.

Use a small bounded number of views per schema. Start with one reviewed Vietnamese view and one reviewed English view. Add more views only when held-out evidence shows a real recall gap.

### 5.3 Authoritative ownership

Localized names, aliases, descriptions, and curriculum phrases belong in catalog or curriculum source data. The projection builder consumes this data generically. It must contain no schema-ID switch, topic-specific phrase table, regular expression tied to a lesson, or hardcoded benchmark answer.

Published physics contracts are immutable. If localized retrieval metadata is stored outside the physics contract, version and checksum it independently. If it changes a published schema definition, publish a new schema version and preserve the old version for replay.

## 6. Multilingual normalization

Implement schema-agnostic normalization with tests for:

- Unicode NFKC normalization.
- Vietnamese composed and decomposed diacritics.
- Vietnamese text with and without accents.
- Common OCR spacing and punctuation noise.
- Decimal comma and decimal point without corrupting values.
- Superscripts and common physics symbols.
- Case-sensitive Latin and Greek physical symbols.
- Unit tokens such as m/s, m/s2, N, V, A, ohm, Ω, F, Hz, and rad.

Use global language resources or configuration for stop words and tokenization. Do not add per-schema stop-word or stemming rules. A unit or one-letter token alone must never create high-confidence selection; confidence requires independent concept, quantity, or contract evidence.

## 7. Embedding and vector index contract

Use a multilingual embedding model only after it passes the Vietnamese and English evaluation fixture. Model popularity or provider documentation is not release evidence.

Every stored semantic vector must pin:

- schema ID and schema version;
- locale;
- semantic view type and view version;
- embedding provider and model;
- embedding dimension;
- projection version;
- normalized source checksum;
- lifecycle state or an immutable link to the lifecycle-controlled schema;
- creation and update timestamps.

Validate vector dimension, finiteness, and nonzero norm before persistence and query. Never mix vectors from different provider, model, dimension, projection, or source checksum identities.

Changing the semantic projection or model must create a new usable index generation. Build it completely, validate it, then swap the active generation atomically. A partial generation must never serve requests.

Keep provider calls bounded by a total deadline and a small retry budget for retryable failures. Do not silently substitute a different model, random vector, or unannounced lexical-only success.

## 8. Retrieval and ranking behavior

1. BM25 must use actual term frequency, document frequency, document-length normalization, and deterministic ties.
2. Semantic retrieval must use cosine distance through PostgreSQL and pgvector with a bounded result set.
3. Fuse one-based lexical and semantic ranks using Reciprocal Rank Fusion: sum of 1 divided by k plus rank. Do not add raw BM25 and cosine scores.
4. Group multiple semantic views by pinned schema identity before the final candidate decision. Prevent one schema with more views from gaining an unfair score merely because it has more rows.
5. Run the generic schema-contract verifier over fused candidates. Evidence may include compatible quantities, units, symbols, required-data coverage, and contradictions derived from compiled contracts.
6. Use global configuration for candidate counts and confidence thresholds. No per-schema threshold is permitted.
7. A learned multilingual reranker may be added behind a typed interface only if measurements show that candidate recall is acceptable while ordering remains below the release gate. It must rank current candidates and must not act as an unrestricted schema-ID classifier.

## 9. AI boundary

The extraction AI receives:

- the normalized problem text;
- the base extraction contract;
- only three to five pinned candidate contracts;
- the exact allowed schema IDs and versions.

The extraction AI must not receive the full catalog, vectors, solver names, formulas, visualization definitions, reference code, or retired candidates.

Strict validation must reject:

- an ID or version outside the pinned candidate set;
- an unknown or duplicate quantity;
- numeric values encoded as strings when the contract requires a number;
- unknown fields;
- incompatible units;
- unresolved ambiguity represented as a successful selection.

Retries reuse the same pinned candidates. They must not broaden to the full catalog.

## 10. MVP evaluation fixture

Create a versioned fixture covering every latest approved schema in CIRCUITS, DYNAMICS, and KINEMATICS. Derive the required identity set from the catalog so new approved MVP schemas cannot bypass the gate.

For every identity include independently written cases across applicable categories:

- Natural Vietnamese with correct accents.
- Vietnamese without accents.
- Vietnamese with realistic OCR noise.
- Natural English.
- Short but answerable statements.
- Closely related confusers within and across the three topics.
- Explicitly ambiguous problems.
- Out-of-scope problems.

Separate calibration and held-out cases. Freeze held-out labels and thresholds before tuning. Record fixture provenance. Synthetic cases must be labeled synthetic; never claim textbook attribution without a page-level source mapping.

Deterministic fake embeddings are allowed for algorithm unit tests. They do not satisfy the multilingual quality gate. The release evidence must include the configured real provider or an approved local multilingual model.

## 11. Mandatory quality gates

For the held-out MVP fixture, require all of the following:

- Candidate recall at 20 is at least 95 percent on answerable supported cases.
- Candidate recall at the configured AI candidate count is at least 95 percent.
- Confident-selection precision is at least 99 percent.
- Confident coverage is at least 80 percent of unambiguous supported cases.
- There are zero confident selections on explicitly ambiguous and out-of-scope challenge cases.
- Every latest approved MVP schema has Vietnamese and English evidence.
- Report raw counts, per-topic results, abstention rate, and confidence intervals.
- Do not lower a threshold after inspecting failures.

The three Vietnamese and three English smoke cases in section 2 must pass, but they are not sufficient release evidence by themselves.

## 12. Latency and operational gate

Measure the retrieval engine separately from final extraction-AI latency. The MVP retrieval target is P95 at or below one second under the declared environment and workload.

Record:

- hardware and operating system;
- database location and pgvector version;
- provider or local embedding model identity;
- corpus and semantic-view counts;
- warm and cold sample counts;
- concurrency;
- normalization, query embedding, lexical, vector, fusion, verification, and total P50/P95/P99 latency.

Do not hide external provider latency or use a warmed cache as the only result. If the configured provider cannot meet the deadline, return an explicit unavailable or ambiguity response and document the measured blocker. Add caching only when measurement proves it is necessary; bound its size and lifetime and exclude raw text from telemetry.

## 13. Implementation order

1. Read applicable repository instructions, current migrations, routing code, catalog generator, evaluation script, and existing report.
2. Run git status and preserve all user changes.
3. Reproduce the current six smoke cases and record candidate ranks.
4. Inventory all latest approved schemas in the three MVP topics and identify missing Vietnamese or English retrieval metadata.
5. Define and validate the versioned multilingual metadata contract.
6. Implement deterministic lexical and semantic projection version 2.
7. Add the required incremental migration and repository queries for multiple semantic views or index generations if the existing table contract cannot support them safely.
8. Implement normalization, BM25, semantic retrieval, grouping, RRF, contract verification, and ambiguity behavior through existing capability packages.
9. Reindex only a local disposable database and verify generation atomicity and lifecycle filtering.
10. Add unit, integration, migration, concurrency, and end-to-end routing tests.
11. Run the frozen held-out evaluation and latency measurement.
12. Update the existing production report with exact commands, results, failures, and remaining blockers.

Run targeted tests while developing. Run complete relevant gates once after integration instead of repeatedly rerunning the full suite after every edit.

## 14. Code organization

Keep ownership within the existing architecture:

    schema/catalog and curriculum metadata
    schema/routing/model
    schema/routing/index
    schema/routing/lexical
    schema/routing/vector
    schema/routing/fusion
    schema/routing/verification
    schema/routing/service
    ai/extraction/prompt
    ai/extraction/validation

Do not create a parallel routing engine. Controllers map HTTP, provider clients own transport, repositories own bounded database queries, projection builders own retrieval documents, and orchestration services coordinate typed components.

## 15. Explicit prohibitions

Do not:

- Hardcode Vietnamese or English phrases to schema IDs in Java, SQL, prompts, or regular expressions.
- Add schema-specific ranking weights, thresholds, candidate lists, or exceptions.
- Train a fixed classifier that memorizes the current schema-ID list as the source of truth.
- Translate every request with an extraction LLM before retrieval.
- Send the full catalog to any AI after a miss or retry.
- Embed formulas, solver code, visualization, sample solutions, or private user input in catalog vectors.
- Treat unit-only overlap as sufficient selection evidence.
- Modify published schema definitions in place.
- Edit applied migrations or delete historical index generations without a verified retention policy.
- Use fake embeddings as production quality evidence.
- Tune against held-out labels or lower release thresholds.
- Start the backend against Supabase to prove local correctness.
- Claim the entire PhysLive catalog or national curriculum is production-ready from this three-topic MVP.

## 16. Required tests

Add focused executable evidence for:

- Vietnamese Unicode, accent-folded shadow tokens, OCR noise, symbols, and units.
- BM25 hand-calculated scoring and stop-word behavior.
- Multiple semantic views grouped without score multiplication.
- Provider/model/dimension/projection/checksum isolation.
- Invalid, zero, non-finite, and wrong-dimension vectors.
- Deterministic RRF and tie-breaking.
- Contract verifier behavior when only a unit matches.
- Candidate pinning across retries and ambiguity resolution.
- Out-of-set AI schema rejection.
- Approval, retirement, topic disablement, reindex, and concurrent generation swap.
- A new MVP schema becoming searchable from metadata without router code changes.
- Real PostgreSQL and pgvector ordering in disposable infrastructure.
- No write access to configured Supabase during tests.

## 17. Completion criteria

The MVP is complete only when:

- Every latest approved CIRCUITS, DYNAMICS, and KINEMATICS identity has validated Vietnamese and English retrieval metadata.
- Lexical and semantic documents are separated, deterministic, versioned, and checksum protected.
- Real BM25, multilingual semantic retrieval, RRF, contract verification, and ambiguity handling run end to end.
- The correct candidate is never lost because multiple semantic views were aggregated incorrectly.
- The AI receives at most the configured three to five pinned contracts.
- All quality and latency gates in sections 11 and 12 pass with current evidence.
- Local migration, reindex, routing, extraction-membership, and failure-path tests pass.
- No Supabase data was modified during implementation or verification.
- Existing non-MVP topics, public APIs, and historical replay have no regression.
- The existing production report contains exact commands and evidence.

Use MVP MULTILINGUAL ROUTING ACCEPTANCE PASSED only when every criterion above passes. Otherwise report MVP MULTILINGUAL ROUTING NOT READY, list the precise failures, and continue all safe work that can still be completed.

## 18. Final delivery

Report:

1. Before and after retrieval architecture.
2. Catalog metadata contract and ownership.
3. Files, packages, and incremental migrations changed.
4. Vietnamese and English fixture coverage by schema and topic.
5. Candidate recall, confident precision, coverage, ambiguity behavior, and confidence intervals.
6. P50/P95/P99 latency by stage and total.
7. Exact test, migration, reindex, evaluation, and build commands with results.
8. Proof that only bounded pinned candidates reach the extraction AI.
9. Proof that no schema-specific routing logic was added.
10. Remaining warnings, blocked external gates, and operational risks.

