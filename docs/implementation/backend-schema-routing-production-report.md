# Backend schema routing and physics implementation report

**Evidence refreshed:** 2026-09-21.

**Trạng thái: NOT PRODUCTION READY.** This implementation advances schema routing, candidate-bounded extraction, catalog integrity, typed output/end-condition contracts, and observability. The full solver/reference migration and all-model output-contract integration remain incomplete. V8/V9 and the Hibernate-first baseline-0 bootstrap through V1–V9 have passed live PostgreSQL/pgvector integration. The checkboxes below describe the repository at this checkpoint; they do not relax the source prompt.

## 1. Baseline and current inventory

Initial `git status --short` showed no pre-existing repository changes other than the user-provided prompt file. No `AGENTS.md` was present. Baseline commands were run before source edits.

| Measure | Baseline | Current checkpoint |
|---|---:|---:|
| Catalog source-version rows / current schema IDs / topics / models | 74 / 74 / 9 / 74 | 138 / 74 / 9 / 74; 64 superseded source rows and 28 older published rows remain available for history/replay |
| Unique numerical/reference IDs in current routed schemas | 50 / 50 | 74 / 74 |
| Shared numerical/reference IDs in current routed schemas | 9 pairs; `applications_*` bound to 8 models across 4 topics | 0 shared pairs among latest routed versions; historical `applications_*` bindings remain shared across 7 models and 4 topics |
| Parameter `from(JsonNode, Map...)` binders | 63 | 63 |
| `PhysicsValues.require/optional` production references | 302 | 302 |
| Solver/reference files mentioning `JsonNode` | 110 | 110 |
| Model/schema dispatch switches in solver/reference packages | 10 | 10 |
| Approved schema catalog serialized to extraction AI | About 108,000 characters / 27,000 estimated tokens | Removed from the extraction provider; max prompt characters 30,000 and candidate top-K 5 |
| Quantity definitions / aliases in current schema versions | 324 / 382 | Latest versions contain 320 quantity definitions and 385 aliases |
| Schema quantity defaults | 11 in the initial 74-row catalog | 36 defaults are declared in current schema versions; parameter-level fallbacks remain on legacy paths |
| Typed per-output declarations | 0 of 74 latest schema versions | 30 of 74 latest versions declare 116 typed outputs (84 time-series, 31 scalar, 1 scalar-field); 44 remain structural-only |
| Catalog mojibake markers | 146 | All 138 source rows pass the versioned text check; older published rows remain preserved in the history archive |

The numerical/reference counts were remeasured after implementation. All 74 latest routed schema identities have typed module bindings with unique numerical/reference pairs. The current source contains 64 prior-version rows across those identities; 28 additional older published rows are stored in the separate history archive. Historical and compatibility paths still retain legacy solver code.

## 2. Implemented changes

### Routing and extraction

- Added immutable search-document, candidate, retrieval-score, and decision models under `backend/src/main/java/com/example/backend/schema/routing/`.
- Search documents are deterministic typed projections of approved schema/curriculum metadata: description and learning outcomes, canonical keys, aliases, symbols, allowed units, relation types, end-condition capabilities, curriculum labels, and source checksum. Formula, solver/reference ID, output, and visualization data are excluded. Curriculum metadata coverage remains incomplete in source data.
- Added Unicode physics tokenization, BM25 with term/document frequencies and length normalization, exact pgvector cosine retrieval, deterministic RRF, generic contract evidence, configured confidence/margin policy, and explicit ambiguity results.
- Added typed `physlive.schema-routing.*` configuration. The current defaults use lexical/vector top-K 20, candidate top-K 5, RRF `k=60`, prompt cap 30,000 characters, query cap 20,000 characters, and embedding model `openai/text-embedding-3-small` at dimension 1536.
- Added an embedding provider abstraction and OpenRouter implementation. The pgvector store checks provider/model/dimension/source checksum and current search text, and receives the exact version-pinned identity set from the approved in-memory snapshot so stale approved embeddings cannot consume top-K slots. Exact cosine search is used; no ANN index is created.
- The extraction provider now uses `ExtractionPromptBuilder` and candidate contract projections. Strict duplicate-key and trailing-token parsing and candidate/version/quantity membership checks happen before POJO binding. A `SELECTED` route requires the exact backend-selected schema/version; an `AMBIGUOUS` route accepts only identities in its pinned set. Current approved identity is checked before the prompt and again after the AI response. Retry reuses the original pinned messages and appends a fixed short repair instruction; ambiguity resolution uses the current pinned approved version.
- The generic reranker recognizes compound unit tokens, explicit quantity-to-value connectors, configured numeric/domain contradictions, and declared `sameUnitAs` relations when both quantities have one unambiguous observed value. Ambiguous, multiple, or distant observations remain unsupported.
- Added routing health/readiness checks and bounded metrics without problem text labels. Readiness compares the index identities with the active approved schema identities. A failed index rebuild invalidates the previous snapshot so a stale retired schema cannot remain routable. Legacy specification adapter invocations and prompt character count are measured without storing raw AI output.

### Catalog, persistence, and compatibility

- Split the schema source into topic-scoped modules under `backend/src/main/resources/schemas/source/`; added deterministic generation and drift checking with `scripts/generate-schema-catalog.mjs`. The latest sound-wave contract moves phase, domain, probe, and sample defaults into compiled schema data.
- Added `scripts/repair-versioned-schema-text.mjs`. Text repairs create newer schema versions; existing persisted versions, runs, and assignment snapshots are not rewritten by the source repair. Typed migrations add dedicated versions and solver/reference pairs for diode characteristic, resistor networks, circular motion, adiabatic and isothermal and isobaric gas, thermal expansion, work-energy power, isochoric gas, RC charge/discharge, linear drag, uniform acceleration, and uniform electric field while retaining the prior published bindings.
- The shared unit catalog now declares the six compound units (`kg/s`, `kg/J`, `J/kg`, `1/m`, `rad/s`, and `dB/m`) needed by catalog contracts. `SchemaCompiler` rejects unknown quantity units and typed output units; current and archived source-unit scans find no missing aliases.
- Preserved 28 older published catalog rows in `schemas/history/published-versions.json`. Bootstrap seeds missing archived schema/solver rows as retired before current approved entries; existing lifecycle values are not changed. Current candidate lookup requires the latest exact approved identity. Historical replay has a separate published-version resolver and a versioned legacy dimensional-suffix adapter; current lookups no longer strip `_1d`/`_2d` suffixes.
- Published schema initialization now checks stored name/topic and canonicalized stored definition against checked-in source even when a checksum already exists. Null checksums are backfilled only after structural equality is established.
- Added V8 pgvector storage and V9 `solver_versions.binding_checksum`. Solver binding backfill verifies solver ID and output definition against source before recording a checksum.
- V8 intentionally has no foreign key to `schema_versions`: Flyway runs before Hibernate creates its tables on fresh installs. The vector query joins embeddings to current approved schema rows and enabled topics.
- Added Testcontainers coverage for V8/V9 from a V7-baselined empty/prior fixture, including pgvector, cosine distance, vector dimension checks, preserved schema JSON, stale search-text reindexing, and exclusion of an old approved vector identity. Both Testcontainers tests now execute and pass with Testcontainers 1.21.4 against PostgreSQL 16.

### Runtime contracts and source quality

- Added sealed typed output variants (`ScalarOutput`, `TimeSeriesOutput`, `VectorSeriesOutput`, `ScalarFieldOutput`) and structural validation, with a versioned legacy serialization adapter. Thirty of 74 latest schema versions declare per-output kind/unit/required contracts (116 outputs: 84 time-series, 31 scalar, and 1 scalar-field); the other 44 latest versions still receive structural-only validation. Numerical modules still return legacy `SolverOutput` rather than typed output frames.
- Added `CanonicalQuantityCompiler` to require raw numeric values and original units, verify canonical keys, re-normalize rather than trust stored normalized echoes, materialize compiled defaults, apply bounded adjustments, and enforce integer/positive/non-negative/minimum/maximum constraints. `SimulationService` binds all 74 latest schema identities to typed modules on initial runs, adjustments, and previews. Historical and compatibility paths still use legacy JSON-based solver implementations.
- Production compiler calls now pass schema ID/version/topic from `SchemaVersion` and enforce the stored checksum; the two-argument inferred-identity overload remains for compatibility/tests. `CompiledSchema` now holds immutable end-condition output-source bindings; `TypedEndConditionCompiler` resolves sources once against that pinned contract, and `SimulationService` reuses the compiled condition through bounded horizon expansion. Historical solver paths use the explicit `LegacyEndConditionJsonAdapterV1`. Resource limits and declarative end-condition capabilities are not yet fully represented in compiled schemas.
- Added an immutable `PhysicsModuleRegistry` that rejects duplicate IDs and mismatched numerical/reference pairs. Its explicit registry currently contains 74 typed modules. New typed bindings use new versioned solver IDs while older schema versions keep their prior bindings.
- Added typed schema-compilation, schema-routing, solver-binding, physics-domain, and output-contract exceptions with stable HTTP mappings. `SimulationService` preserves typed failures through preview and persistence catches instead of flattening server defects into 422 responses.
- Added typed v2 bindings for `light_interference@1.2` and `wave_pulse@1.2`, leaving v1.1 solver pairs intact for replay. Light interference accepts optional `path_difference_rate` (default `0 m/s`), exposes phase in radians, and has dynamic and signed-phase goldens. Wave-pulse probe rendering binds to the canonical `probe_position`; its tests cover Gaussian derivatives, advection, grid density, and resource bounds.
- Added typed v2 bindings for `wave_superposition@1.1` and `wave_reflection@1.1`, leaving v1.0 solver pairs intact for replay. Superposition has direct component-sum numerics and an independent phasor oracle. Reflection uses a schema-owned coefficient with a compiled `-1` default instead of interpreting boundary relations at runtime; wall-node/free-end goldens, oracle checks, unit/domain checks, sampling, and resource bounds are covered.
- Added an unconditional highest-priority startup audit over the active catalog: every latest schema identity must resolve through one typed numerical/reference module pair. `SimulationService.run()` now resolves the exact approved schema/version and fails before legacy registry lookup or persistence if a current pair is missing. `LegacyPhysicsExecutionAdapterV1` permits historical raw execution only for an exact schema/version/binding-version/solver-pair tuple present in superseded source rows or the archived published catalog; the same authorization guards the reference path.
- Added typed bindings for `kinematics_projectile@1.10`, `string_wave@1.2`, `magnetic_force@1.2`, `radiation_safety@1.1`, and `point_charge_field@1.1`; earlier published versions remain pinned for replay. Tests cover hand-calculated projectile and inverse-square cases, wave sampling and derivatives, magnetic near-parallel limits, independent reference formulations, domain boundaries, and resource bounds.
- `SolverOutput` now preserves first-class scalar outputs independently from time series. Ten latest schema versions declare scalar outputs and persist them separately: `ac_rlc_circuit@1.2`, `astronomical_telescope@1.2`, `compound_microscope@1.2`, `electromagnetic_induction@1.2`, `magnetic_force@1.2`, `point_charge_field@1.1`, `radiation_safety@1.1`, `sensor_op_amp@1.1`, `simple_magnifier@1.2`, and `thermistor_response@1.1`. A versioned legacy adapter exposes a scalar as a one-element array only at the old `values` response boundary. Schema compilation verifies quantity and declared output units against the shared unit catalog and carries per-output absolute/relative tolerances and numeric/exact/discrete comparison into `PhysicsValidationService`. Thirty latest versions have typed declarations; the other 44 and the solver return type still need full typed output integration (ADR 0007).
- Replaced the audited request-path full-table filters with repository queries: Library search/community applies visibility, moderation, active state, and topic in JPA; school revenue uses one aggregate projection; finalized benchmark selection runs its adjudication/consensus rule in the database. PostgreSQL-backed tests cover the aggregate and finalization query behavior. Other administrative endpoints still expose unpaged list results.
- Added typed end-condition contracts and a strategy registry for time limit, threshold, event, cycle count, and manual conditions. Typed module paths bind source quantities against compiled output/visualization declarations once per run; undeclared, ambiguous, and wrong-group sources fail before solving. Historical solver paths use the versioned V1 JSON adapter.
- Added a Java source encoding scanner that checks all files under `backend/src/main/java` and `backend/src/test/java`, and self-tests common corruptions while allowing Vietnamese and Greek symbols.
- Added ADRs `docs/adr/0005-hybrid-schema-routing.md`, `docs/adr/0006-pgvector-schema-index.md`, and `docs/adr/0007-typed-scalar-output-contract.md` documenting decisions and open gaps.

## 3. Verification evidence

### Backend

| Command | Result |
|---|---|
| Baseline `cd backend && .\\mvnw test` | 68 tests, 0 failures/errors |
| Current clean test `cd backend; $env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'; .\mvnw clean test` | `BUILD SUCCESS`; 461 tests, 0 failures/errors/skips; includes fresh bootstrap, pgvector lifecycle/query integration, current typed-binding startup audit, exact historical adapter permits, fail-closed current simulation, candidate pinning, typed end-condition binding, fault injection, and routing-to-persistence tests |
| Focused module/catalog/compiler selector | 80 tests, 0 failures/errors/skips; includes all added typed module batches and current catalog-to-module binding checks |
| Focused candidate/lifecycle/replay selector | 28 tests, 0 failures/errors/skips; exact selected candidate, stale schema rejection, fail-closed index refresh, health identity, versioned suffix replay, and typed validation/replay tests |
| Current focused regression selector `-Dtest=GlobalExceptionHandlerTypedMappingsTest,SimulationServiceFailureMappingTest,SchemaCompilerIdentityTest,PhysicsModuleRegistryTest,OutputContractValidatorTest,SchemaRoutingServiceTest,PhysicsValidationServiceTypedModuleTest,SchemaSearchDocumentBuilderTest,SchemaContractRerankerTest,PgVectorSchemaLifecycleIndexIntegrationTest,SchemaIdentityResolutionTest` | 33 tests, 0 failures/errors/skips; typed HTTP mappings/catch preservation, document projection, reranking evidence, live pgvector lifecycle fail-closed behavior, replay pinning, and independent reference fault injection |
| Focused end-condition/compiler/compatibility selector `-Dtest=TypedEndConditionCompilerTest,EndConditionStrategyRegistryTest,SimulationServiceTypedPathTest,SimulationServiceScalarPersistenceTest,SchemaCompilerIdentityTest,SchemaCompilerOutputValidationTest,PhysicsValuesCanonicalTest` | 26 tests, 0 failures/errors/skips; end-condition source pinning and reuse, legacy adapter bridge, compiled schema validation, and scalar persistence |
| Current runtime-boundary selector `-Dtest=ApprovedLatestPhysicsModuleBindingAuditTest,LegacyPhysicsExecutionAdapterV1Test,SimulationServiceLegacyBoundaryTest,SimulationServiceTypedPathTest,SimulationServiceScalarPersistenceTest,PhysicsValidationServiceTypedModuleTest,PhysicsValidationServiceFaultInjectionTest,SchemaIdentityResolutionTest,SchemaRoutingSimulationPersistenceIntegrationTest` | 21 tests, 0 failures/errors/skips; active catalog latest-pair audit, exact historical permits, current suffix identity rejection, no current legacy fallback/persistence, typed execution, and routing-to-persistence |
| Fresh bootstrap `cd backend; $env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'; .\mvnw '-Dtest=FreshSchemaBootstrapMigrationTest' test` | `BUILD SUCCESS`; Hibernate-created empty schema, Flyway baseline 0, V1–V9 all applied; final Hibernate validation and representative indexes/constraints/columns verified |
| `cd backend; $env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'; .\mvnw '-Dtest=PgVectorSchemaSearchMigrationTest' test` | `BUILD SUCCESS`; 2 PostgreSQL/pgvector integration tests passed on PostgreSQL 16 |
| `node scripts/check-backend-source-encoding.mjs` | Pass |
| `node scripts/generate-schema-catalog.mjs --check` | Pass; 138 entries |
| `node scripts/repair-versioned-schema-text.mjs --check` | Pass; 138 entries |
| Static scan: `rg -l '\bJsonNode\b' backend/src/main/java/com/example/backend/physics/solver backend/src/main/java/com/example/backend/physics/reference` | 110 solver/reference files still mention raw JSON |
| Static scan: `rg -n 'PhysicsValues\.(require\|optional)' backend/src/main/java` | 302 production references remain |
| Static scan: `rg -n 'switch\s*\(' backend/src/main/java/com/example/backend/physics/solver backend/src/main/java/com/example/backend/physics/reference` | 10 dispatch switches remain |
| Static scan: `rg -n 'findAll\(' backend/src/main/java` | 8 calls remain; the audited Library search/community, school revenue aggregate, and finalized benchmark filters now use repository queries. Other administrative list endpoints remain unpaged |
| Static scan: current latest-per-schema catalog dimensional suffix check | 0 IDs end in public `_1d`, `_2d`, `-1d`, or `-2d` |
| Public schema/model/solver identity suffix scan over topic source files | No public dimensional `1d`/`2d` suffix found |
| Wildcard import scan over production Java | 28 existing wildcard imports found; no new wildcard import appears in tracked changes |
| `git diff --check` | Pass; Git prints LF-to-CRLF working-copy notices |

V8/V9 pass against live PostgreSQL 16 from both an empty database fixture and a represented V7 prior-schema fixture. `FreshSchemaBootstrapMigrationTest` proves the existing V1–V9 chain succeeds for a verified empty database after Hibernate creates entity tables, Flyway baselines at version 0, and then applies all migrations. A normal one-pass Flyway startup directly against an empty schema still fails at V4 because `assignment_submissions` does not exist. The tested operator sequence is documented in `docs/implementation/fresh-database-bootstrap.md`. The test dependency is pinned to Testcontainers 1.21.4 because 1.19.8 is incompatible with this Docker Engine 29 environment. No already-applied migration was edited.

### Frontend and catalog coverage

| Command | Result |
|---|---|
| Baseline `cd react-client && npm run check` | Pass; 19 tests |
| Current `cd react-client && npm run check` | Pass; architecture, scene contract, lint, 23 tests, TypeScript, and Vite build |
| Scene contract check (part of `npm run check`) | 138 schemas; 66 explicit scene graphs; 72 series-driven scenes; 0 errors |
| `node scripts/check-curriculum-coverage.mjs` | 90/90 internal curriculum rows approved; 85/85 lessons mapped; no errors |
| `node scripts/report-topic-coverage.mjs` | 85/85 lessons mapped across 9 topics; 100% internal topic coverage |
| Official-program evidence | Not asserted; the 100% figure is internal catalog coverage |
| `git diff --check` | Pass; Git prints LF-to-CRLF working-copy notices |

Vite still warns about large emitted chunks: PDF worker 2395.54 KB, PixelBlast 546.99 KB, and main index 504.20 KB. Maven also reports existing deprecated API usage in `AcRlcParameters`, dynamic Byte Buddy agent-loading warnings, and PostgreSQL Testcontainers teardown connection warnings. Surefire logged its 30-second fork shutdown notice after the final tests had passed; the command still returned `BUILD SUCCESS`.

### Focused test evidence

- BM25: hand-calculated fixture, Unicode/Vietnamese/symbol tokens, document length handling, deterministic ranking.
- RRF: hand-calculated fixture and deterministic ties.
- Routing: fake vector retriever, confidence/margin ambiguity, pinned candidates, and metrics.
- Prompt/AI: bounded candidate-only projection, no solver/formula/visualization fields, duplicate-key rejection, response size/depth limits, strict shape, exact selected-candidate membership for `SELECTED`, pinned-set membership for `AMBIGUOUS`, canonical duplicate rejection through aliases, and relation/end-condition membership checks where declared. Current approved identity is rechecked before prompting and after the AI response.
- Typed outputs: scalar/time-series/vector/field structure and finite/shape checks. Thirty of 74 latest schemas have per-output declarations; 10 persist first-class scalars, and output-unit catalog verification/per-output tolerances are consumed for declared contracts. The other 44 schema versions still use structural-only checks, and all numerical modules retain the legacy `SolverOutput` container.
- Typed runtime: 74 modules, including projectile motion, string waves, magnetic force, radiation safety, point-charge field, circuit/optics instruments, modern physics, dynamics graphs, radio, ultrasound, and practical data models; focused tests cover goldens, boundaries/domain checks, module registration, schema default materialization, independent references, and resource limits.
- Canonical ingress: raw value/unit normalization, default materialization, bounded adjustment, alias rejection past ingress, duplicate/malformed key rejection, and declared integer constraint checks.
- End conditions: time-limit/threshold/cycle/contact contracts bind against declared output sources; undeclared and wrong-group sources are rejected; `SimulationService` compiles once and reuses the condition across horizon expansion. Trim behavior covers timeseries/scalar fields.
- Catalog integrity: canonical JSON comparison, safe backfill, published metadata drift, and solver binding checksums.
- Catalog history: 28 superseded entries compile, remain separate from the active route catalog, and are seeded as retired before current approved entries in the bootstrap test.
- Fault injection: two test-only cases reject deliberately perturbed numerical output, including a typed projectile y-series shifted by +2 m against its independent reference. This is not a per-model mutation suite.

`SimulationServiceTypedPathTest` exercises a persisted typed simulation after a prepared specification exists. `SchemaRoutingSimulationPersistenceIntegrationTest` now composes real schema routing, candidate membership checks, raw-unit normalization at the extraction boundary, typed execution, reference validation, and a captured persisted run snapshot using a deterministic fake extractor.

## 4. Required checklist at this checkpoint

Status values: **DONE** means the complete checkbox is met; **PARTIAL** means relevant scaffolding exists but the prompt's full condition is not met; **NOT DONE** means implementation/evidence is absent; **BLOCKED** means the gate could not run in this environment.

### Schema routing

| Status | Requirement | Evidence / gap |
|---|---|---|
| DONE | AI prompt does not serialize the full approved catalog | `OpenRouterExtractionProvider`, `ExtractionPromptBuilderTest` |
| PARTIAL | Search document generated from approved schema/curriculum metadata | `SchemaSearchDocumentBuilder`; not every curriculum relation is represented in schema data |
| DONE | Real BM25 implementation, not keyword `contains` | `Bm25SchemaRetriever`, hand-calculated tests |
| PARTIAL | pgvector persists/retrieves correct model, dimension, checksum | `V8__add_hybrid_schema_search.sql`, `PgVectorSchemaEmbeddingStore`; PostgreSQL 16 integration tests pass; admin dry-run/reindex exists and readiness checks active identity, but broader failure/reindex operations still need production evidence |
| DONE | Deterministic RRF | `ReciprocalRankFusionTest` |
| DONE | Generic contract reranker without schema/topic branches | `SchemaContractReranker` |
| DONE | Low confidence/margin becomes explicit ambiguity | `SchemaRoutingServiceTest` |
| PARTIAL | New schema routes after approval/index without router code changes | Startup rebuild, lifecycle refresh, topic-toggle refresh, and admin dry-run/reindex exist. `PgVectorSchemaLifecycleIndexIntegrationTest` checks live approved-version swaps, retired/draft/disabled exclusion, embedding-failure invalidation, and readiness; production admin reindex operations still need broader evidence |

### AI contract

| Status | Requirement | Evidence / gap |
|---|---|---|
| DONE | AI sees only bounded candidate contracts | top-K 5, prompt cap 30,000 characters, prompt tests |
| DONE | AI contract excludes visualization, solver implementation, formulas, and oracle values | `CandidateContractProjection`, `ExtractionPromptBuilderTest` |
| DONE | Backend rejects schemas/versions outside the candidate set | `StrictSpecificationValidator`; `SELECTED` exact identity and `AMBIGUOUS` pinned-set regression tests |
| DONE | Strict JSON shape and duplicate keys rejected before POJO binding | `StrictJsonParser`, strict validator tests |
| PARTIAL | Backend canonicalizes quantities and normalizes raw value/unit | extraction ingress does so; simulation solver APIs still receive JSON and aliases/legacy data |
| PARTIAL | Retry and ambiguity resolution retain pinned candidates/version | Retries reuse the pinned decision; ambiguity resolution requires a current pinned approved version. Full retry/replay integration matrix remains incomplete |

### Canonical physics runtime

| Status | Requirement | Evidence / gap |
|---|---|---|
| PARTIAL | Solver/reference runtime does not receive raw `JsonNode` | All 74 latest schema identities use typed modules; current simulation requests resolve exact identities and missing current pairs fail closed. Historical compatibility still retains 110 raw-JSON solver/reference files |
| PARTIAL | One immutable canonical quantity bag compiled once per request | `CanonicalQuantityCompiler` compiles the latest typed request once; historical/compatibility solver paths retain `JsonNode` bridges and override maps |
| PARTIAL | Alias resolution only at ingress | Latest typed module paths receive exact canonical keys and reject aliases; 302 `PhysicsValues.require/optional` references remain in legacy solver implementations |
| PARTIAL | Defaults have one source in compiled schema | Latest typed paths materialize schema defaults; parameter-level fallbacks remain in legacy solver implementations |
| PARTIAL | Legacy data flows only through a versioned replay adapter | Numerical and reference raw solver calls are reachable from production services only through `LegacyPhysicsExecutionAdapterV1`, which checks exact historical identity and rejects latest active schemas. `LegacySchemaIdentityAdapter` and `LegacyEndConditionJsonAdapterV1` remain versioned. Adapter unit tests cover exact permits; persisted historical-run replay through the adapter still lacks an end-to-end test |
| NOT DONE | `PhysicsValues` absent from production path | 302 references remain in legacy solver implementations |

### Modules and academic independence

| Status | Requirement | Evidence / gap |
|---|---|---|
| PARTIAL | Immutable registry fails on duplicates/missing bindings | Typed registry rejects duplicate IDs and mismatched pairs; an unconditional startup audit checks all 74 latest active catalog identities. Historical raw permits and all persisted DB bindings do not yet have a complete startup audit |
| NOT DONE | New model requires no central model switch | 10 model switches remain across solver/reference packages |
| NOT DONE | No multi-topic solver/reference ownership | Latest routed IDs have unique pairs; historical `applications_*` bindings still span 7 models and 4 topics |
| NOT DONE | Reference formulas are independent of numerical path | Many legacy parameter records expose formulas shared by numerical/reference paths; independent reference coverage remains incomplete |
| PARTIAL | Every model has golden, invariant, boundary, and invalid-domain evidence | Existing test-source scan finds all 74 model IDs, but golden, invariant, boundary, and invalid-domain evidence is still uneven and the required model-by-model matrix is incomplete |
| PARTIAL | Mutation/fault injection proves incorrect numerical output is detected | `PhysicsValidationServiceFaultInjectionTest` rejects one deliberately perturbed series against an independent test oracle; per-model mutation/fault evidence is absent |

### Typed outputs and end conditions

| Status | Requirement | Evidence / gap |
|---|---|---|
| PARTIAL | Domain output distinguishes scalar/series/vector/field | Sealed hierarchy and validator exist; 30/74 latest schema versions declare kind/unit/required contracts, but production modules still return map-shaped `SolverOutput` and 44 latest schemas lack per-output declarations |
| PARTIAL | Key/kind/unit/shape/finiteness all validated before persist | Enforced against 30/74 latest schemas with per-output declarations; 44 latest schemas use structural-only validation, and duplicate keys cannot be represented after `SolverOutput` map construction |
| DONE | Per-output absolute/relative/exact/discrete tolerance | `SchemaCompiler` compiles numeric/exact/discrete comparison and absolute/relative tolerances; `PhysicsValidationService` applies the compiled contract; compiler/fault-injection tests cover it |
| PARTIAL | Typed end-condition strategy and compiled output binding | Typed strategy registry, immutable compiled source bindings, and per-run contract compilation exist for typed modules; historical paths use a versioned adapter and end-condition/resource capabilities are not fully compiled from catalog definitions |
| PARTIAL | Trim/replay preserves every typed output shape | Scalar-field/time-series trim tests and scalar persistence integration exist; historical full-shape replay regression remains absent |

### Catalog and persistence

| Status | Requirement | Evidence / gap |
|---|---|---|
| DONE | Topic/model source modules and deterministic generation | `schemas/source/`, `generate-schema-catalog.mjs --check` |
| PARTIAL | Schema and solver bindings have checksum drift protection | Schema definition/name/topic and solver ID/output binding are checked; V9 adds solver checksum. PostgreSQL 16 V8/V9 integration tests pass; full schema bootstrap remains incomplete |
| DONE | Null checksum backfill verifies stored content before writing | `SchemaCatalogIntegrity`, tests |
| DONE | Published versions are not mutated by catalog bootstrap | Drift fails with instruction to create a new version; text repair uses new versions |
| PARTIAL | Historical run/assignment snapshots remain replayable | V8/V9 do not rewrite them, but no end-to-end replay regression test ran |

### Quality and operations

| Status | Requirement | Evidence / gap |
|---|---|---|
| PARTIAL | Large services split by responsibility | Prompt building/routing separated; `ProblemService` and `SchemaDefinitionService` remain large |
| DONE | Request paths do not `findAll()` then filter in memory | The audited library visibility/topic filters, school revenue summary, and benchmark finalization now execute in repository queries; the eight remaining `findAll()` calls are unfiltered administrative lists |
| DONE | Typed exception taxonomy and HTTP mappings | Core schema compilation/routing, solver binding, physics-domain, output-contract, canonical-contract, and embedding failures have typed HTTP mappings; `SimulationServiceFailureMappingTest`, `GlobalExceptionHandlerTypedMappingsTest` |
| PARTIAL | Mojibake is checked and repaired | Active catalog source and all backend Java source/test files pass the UTF-8 scanner; the preserved historical archive still contains legacy display text to retain old published definitions |
| PARTIAL | No wildcard imports added in bulk | 28 wildcard imports remain in pre-existing production files; none were added by the current tracked edits |
| PARTIAL | Validated config, health, metrics, and reindex operation | Config, health, metrics, `SchemaRoutingAdminController` dry-run/reindex, and idempotent service rebuild exist. Provider circuit breaker, query embedding cache, readiness/reindex integration coverage, and some operational metrics remain incomplete |
| NOT DONE | No duplicated alias/default/output definitions across layers | Legacy parameter binders and schema/output compatibility metadata still duplicate contract details |
| DONE | `git diff --check` is clean | Pass, with Git line-ending notices |

### Build and test gates

| Status | Requirement | Evidence / gap |
|---|---|---|
| DONE | Backend clean full test | `./mvnw clean test` passes 461 tests, including fresh bootstrap, live PostgreSQL/pgvector integration, current binding audit, historical permit checks, and routing-to-persistence; zero failures/errors/skips |
| DONE | PostgreSQL/pgvector migration integration | V8/V9 pass against empty and represented-prior fixtures; `FreshSchemaBootstrapMigrationTest` applies full V1–V9 after Hibernate schema creation and baseline 0 |
| DONE | Full routing → AI fake → canonical runtime → solver → validation → persistence | `SchemaRoutingSimulationPersistenceIntegrationTest` composes schema routing, strict fake extraction and candidate membership, raw-unit normalization, typed execution, reference validation, and captured persisted run snapshot |
| DONE | Frontend architecture/lint/test/build | `npm run check` passes all steps and 23 tests |
| DONE | Scene contract | 138 schemas, 66 explicit scene graphs, 72 series-driven scenes, zero errors |
| DONE | Curriculum/topic coverage does not decrease | 90/90 curriculum rows and 85/85 lessons across 9 topics |
| DONE | No new public dimensional suffix IDs | Current catalog identity fields contain no public `1d`/`2d` suffix |
| PARTIAL | No API/replay/assignment snapshot regression | Snapshot-preserving migrations were inspected, but full replay gate is absent |
| PARTIAL | Warnings classified | Deprecated API, Byte Buddy, PostgreSQL/Hikari teardown, Surefire 30-second fork shutdown notice, and Vite chunk-size warnings remain; backend still exits successfully |

## 5. Compatibility, migration behavior, and remaining risks

- Existing persisted schema versions and simulation/assignment snapshots are not rewritten. Current catalog display-text repairs use new schema versions.
- The 28 archived pre-correction versions are exact historical schema data; some legacy display text may remain corrupted in that retired archive to preserve definition checksums. The archive is loaded for pinned replay and is never used to build search documents or AI prompt candidates.
- Missing archive rows are inserted as retired by bootstrap before active catalog entries. Existing rows keep their lifecycle status. Version-pinned schema and solver lookup accepts non-draft retired entries so replay does not require reactivation.
- V8 stores derived search data. V9 adds a nullable solver binding checksum, backfilled only after a source match.
- New extraction requests use candidate routing. `SimulationService.run()` requires the exact latest approved identity; the dimensional-suffix adapter is only used by the published-version replay resolver. Current execution cannot fall back to raw solver/reference methods, while a full persisted historical-run replay integration test remains a gap.
- `SchemaRoutingHealthIndicator` requires the vector extension and a ready index when routing is enabled. Embedding failure does not silently fall back to lexical retrieval.
- Vector search is scoped to the current approved `schemaId@version` snapshot. Both pgvector integration tests pass against PostgreSQL 16 and cover an older approved embedding that would otherwise occupy a top-K slot.
- Schema lifecycle and topic enable/disable changes refresh the search index after commit. A failed rebuild leaves routing unready, and health checks index identity against current approved schemas. Live PostgreSQL coverage now exercises approved-version changes, retired/draft/disabled exclusion, and fail-closed embedding failure. Provider circuit breaker, query embedding cache, projection-format checksum identity, and a full admin reindex endpoint integration test remain incomplete.
- A direct, one-pass Flyway clean bootstrap remains unsupported because the existing V4–V7 migrations expect Hibernate-created base tables. The verified Hibernate-first, baseline-0 fresh-install sequence is documented and covered by `FreshSchemaBootstrapMigrationTest`. It is restricted to an empty database; do not enable baseline-on-migrate for an existing or partially initialized database.
- All 74 current routed schema identities use typed modules. Legacy JSON-based solver/reference implementations remain for historical/compatibility paths; there is no complete all-model independent oracle matrix, fully typed solver output integration, or per-model mutation suite.

Until these gaps are closed and all blocked gates are run successfully, do not describe the backend as production ready.
