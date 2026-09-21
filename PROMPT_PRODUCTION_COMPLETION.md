# PhysLive: Complete the Existing Production Architecture

## 1. Mission and authority

Act as the implementation owner inside this repository. Complete the remaining work for a maintainable, data-driven schema-routing and physics backend, with reproducible release evidence. Implement the changes; do not stop at an architectural proposal or prototype.

This is one continuous implementation task, without a calendar or timeline. Work in dependency order and continue while useful work is possible. A single prompt is not permission to conceal blockers or promise that external infrastructure will be available.

Read `PROMPT_HOAN_THIEN_BACKEND_SCHEMA_ROUTING_PRODUCTION.md` as the original acceptance specification. This document defines the execution order, scope controls, and evidence requirements for completing it. Preserve all original acceptance requirements. Do not reinterpret an unfinished requirement as optional to obtain a passing result.

Use `docs/implementation/backend-schema-routing-production-report.md` as the only progress and evidence report. Maintain a one-to-one mapping to every checkbox in section 23 of the original prompt, including requirements that were omitted or combined in the current report. Do not create another checkpoint report.

## 2. Scope and stop conditions

The release scope is the existing approved physics catalog, schema retrieval, extraction, canonical execution, validation, persistence, historical replay, and the infrastructure directly required to run these flows.

Allowed changes include relevant backend code, schema/unit/curriculum metadata, incremental migrations, focused tests, existing CI, and existing operational documentation. Change frontend code only when necessary to preserve the existing API/scene contract. Change curriculum metadata only with traceable evidence.

Do not expand into new physics topics, new product features, UI redesign, mobile work, payments, school administration, new OCR systems, or unrelated service rewrites. Do not add a new vector database, message broker, search cluster, orchestration platform, or microservice. Keep PostgreSQL/pgvector, the current application framework, and the current frontend architecture.

Implement and verify locally and in available test infrastructure. Do not deploy to production, alter live data, publish, push, or change credentials without existing explicit authorization for that action.

If a required external gate cannot run, finish independent work and report `BLOCKED` with the exact missing prerequisite. Do not replace a real database/provider gate with a fake and label it equivalent. Never claim production readiness with an unresolved mandatory condition.

## 3. Establish the current state once

1. Read applicable `AGENTS.md`, the original prompt, the production report, `docs/README.md`, relevant ADRs, CI, and the complete files you will modify.
2. Run `git status`. Preserve user changes. Do not reset, clean, restore, or overwrite unrelated work.
3. Inventory active schema identities, versions, numerical/reference bindings, typed output coverage, legacy execution entry points, and current tests. Counts in older reports are hints, not acceptance targets.
4. Map every original acceptance requirement to implementation evidence, test evidence, and a specific remaining gap. Reconcile contradictory or stale report entries before treating them as blockers.
5. Run a baseline of the relevant existing gates before changing their behavior. Record the commit/worktree identity, exact command, environment, result, and pre-existing failures. Run the full baseline once; reuse its evidence until a change invalidates it.

The previous report described 74 current schema identities, output declarations on 30 of them, 44 remaining output-contract gaps, substantial historical JSON execution, incomplete independent reference evidence, and a Hibernate-first database bootstrap. Verify all of these claims against the current tree.

Do not report an estimated completion percentage. Report verified requirements and remaining release blockers.

## 4. Architecture and flexibility rules

Preserve this execution boundary:

```text
Problem/OCR text
  -> Unicode normalization
  -> BM25 + embedding/pgvector retrieval
  -> Reciprocal Rank Fusion
  -> catalog-derived contract evidence and confidence decision
  -> bounded, pinned candidate contracts
  -> AI extraction
  -> strict JSON and candidate membership checks
  -> canonical key resolution and backend unit normalization
  -> schema defaults and domain validation
  -> immutable canonical runtime context
  -> typed model binder and numerical module
  -> typed output validation and independent reference validation
  -> compiled end-condition strategy
  -> immutable versioned run snapshot
```

- Schema/quantity aliases, symbols, accepted units, defaults, constraints, output contracts, and curriculum links belong in their authoritative catalog data.
- Physical equations belong in typed backend numerical/reference implementations. A new physical algorithm can legitimately require a new module. A new schema using an existing algorithm must not require editing the router, central dispatcher, or fixed AI prompt.
- Model-specific typed parameter access is legitimate. Schema/topic keyword branches in retrieval, ad hoc unit conversion branches, duplicated defaults, and growing central model switches are not.
- Use immutable registries with duplicate and missing-binding checks. Do not hide dispatch in reflection, service locators, class-name strings, or a formula interpreter.
- Generic algorithms must be driven by validated global policy and compiled metadata. Do not introduce per-schema ranking weights, hand-picked candidate lists, or benchmark-specific exceptions.
- Preserve the meaning of case-sensitive physical symbols. Do not globally lowercase identities or strip dimensional suffixes from new requests.

## 5. Execution order

Complete coherent vertical slices. Reuse working implementations and existing tests. Only refactor a working component when a concrete acceptance gap requires it.

### A. Close typed output and canonical execution gaps

1. Enumerate all active output contracts from the catalog and actual module behavior. Resolve discrepancies explicitly; do not infer units or kinds from sample values or field names.
2. Add missing key/kind/unit/required/shape/tolerance declarations as new schema versions when published content changes. Preserve old definitions and bindings for replay. Regenerate derived catalog artifacts using the existing generator.
3. Make typed output the numerical module domain contract. Keep legacy API serialization in an explicit versioned mapper. Validate duplicate keys before constructing a map that would discard them.
4. Validate declared keys, required probes, kind, unit, dimensions, axes, lengths, finite values, strictly increasing time, resource limits, and per-output comparison before persistence. Preserve scalar semantics and vector/field shape through trimming and serialization.
5. Compile quantities once per execution into an immutable bag and reuse one context containing the pinned compiled schema, binding, quantities, output contract, and end condition. A new adjustment run gets its own context. A solver/reference must never parse JSON, aliases, original units, or root-field fallbacks.
6. Materialize defaults and conversions at ingress from compiled definitions. Parameter records hold typed data and basic invariants, not parsers or shared solver formulas.
7. Finish typed time-limit, threshold, event, cycle-count, and manual strategies using compiled source bindings. Validate declared events, deterministic crossing interpolation, bounded horizon expansion, and trimming for each supported shape.

Exit evidence: every active schema compiles with a complete output contract; invalid output cannot be persisted; every current execution uses the canonical typed boundary; all end-condition strategies have meaningful simulation-path evidence.

### B. Prove physics correctness and historical compatibility

1. Maintain one machine-readable test manifest keyed by catalog identity/model, with links to executable golden, boundary, invalid-domain, invariant, output, and fault-detection cases. Derive the required model set from the catalog. A string occurrence in a test file is not coverage.
2. Supply independent expected values from documented hand calculations or authoritative academic sources. Do not generate expected results by calling the numerical implementation under test.
3. References must not call numerical solvers or share the formula implementation they validate. Sharing units/constants and elementary domain primitives is acceptable only when it cannot reproduce the same formula defect; document the boundary in the existing relevant ADR.
4. Inject representative numerical defects for every model, such as a sign, scale, offset, or output-shape error, and prove the actual validation path rejects them. Test-only injection must not become a production bypass. Avoid an expensive repository-wide mutation framework if targeted tests provide the required evidence.
5. Route historical payloads through explicit versioned adapters authorized by exact schema/version/binding identity. Prefer translating historical input to typed parameters. If exact old behavior requires a historical implementation, keep it owned by the compatibility package with an immutable permit set and replay evidence. No current request may enter it.
6. Remove `PhysicsValues` and raw JSON dependencies from current numerical/reference paths. Any unavoidable legacy helper must be confined to versioned compatibility and guarded by architecture tests, as allowed by the original specification. Do not merely rename a raw API to make a static scan pass.
7. Remove extensible central model switches and mixed-topic production solvers. Historical dispatch must be explicit, bounded, versioned, and unable to accept newly approved schemas.
8. Prove historical run and assignment replay against a real test database: persist fixtures with pinned identities, reload through the application service, replay through the permitted path, and compare outputs and snapshot identity/content. Cover supported output shapes, retired versions, legacy aliases/units/defaults, and unavailable/unauthorized bindings.
9. Audit active and historical bindings against their own lifecycle rules. Retired data must remain replayable without becoming eligible for new routing. Do not require an active schema for historical replay.

Exit evidence: executable per-model coverage, independent oracles, detected injected faults, no current JSON/legacy fallback, and persisted historical replay without snapshot rewriting.

### C. Finish routing quality and operational behavior

Preserve actual BM25 term/document frequency and length normalization, cosine retrieval in PostgreSQL, and rank fusion using `sum(1 / (k + rank))`. Keep validated global configuration and deterministic identity tie-breaks. Do not sum raw BM25 and cosine scores.

1. Complete search documents from approved schema and linked curriculum metadata. Exclude formulas, solver details, visualization, output samples, and user data. Include a projection-format version in the index identity alongside source checksum, provider, model, and dimension.
2. Keep immutable lexical snapshots and bounded database vector queries. Prevent mixed index generations and stale lifecycle candidates during concurrent approval, retirement, topic toggles, reindex, and requests. Swap a usable generation atomically; otherwise expose explicit unready behavior.
3. Validate embedding dimensions and finiteness. Handle timeout, 429, transient server failure, malformed responses, and provider outage with a bounded total request deadline and explicit failure policy. Use bounded retries only for retryable failures and honor applicable retry delays. Never retry indefinitely or silently produce random/lexical-only results.
4. Implement a circuit breaker if required by measured failure behavior, or retain a demonstrably bounded failure policy satisfying the original prompt. Query caching is optional: add bounded TTL/size caching only if measurements justify it. Key any cache by normalized query and embedding identity; do not persist raw user text or expose it in telemetry.
5. Verify the admin reindex endpoint, authorization, dry-run non-mutation, idempotence, concurrent invocation behavior, partial failure/recovery, provider/model changes, and readiness using integration tests. Never delete historical schema/run data during reindex.
6. Pin candidate identities and contracts through extraction retries and ambiguity resolution. Strict validation precedes POJO binding and schema lookup. Reject out-of-set IDs, numeric strings, duplicate fields/quantities, unknown fields, and invalid units. Normalize raw values and original units in the backend; never trust AI-derived normalized values.
7. Record safe bounded metrics for retrieval/fusion latency, candidate count, ambiguity, selected rank, prompt size, embedding failure, and compatibility usage. Keep raw text, prompts, images, secrets, and unbounded identifiers out of logs and metric labels.

Build a versioned retrieval evaluation fixture with provenance, labeled acceptable identities or candidate sets, and answerable/ambiguous/out-of-scope labels. Include every active model, independently phrased held-out questions, Vietnamese symbols/units, OCR-like noise, and closely related confusers. Use the supplied curriculum/book evidence where available and label synthetic examples honestly. Do not fabricate book attribution.

Use separate calibration and held-out sets. Freeze labels and release thresholds before tuning. Proposed minimum release gates are candidate recall@configured-K >= 95% on supported answerable held-out cases, confident-selection precision >= 99%, and zero confident guesses on the explicitly ambiguous/out-of-scope challenge set. Publish raw counts, confidence intervals, abstention rate, and per-topic results; these sample results do not establish universal accuracy. Measure top-1 and coverage together so abstaining on everything cannot pass: confidently resolve at least 80% of labeled unambiguous supported cases. Do not lower thresholds after seeing failures.

Measure end-to-end and per-stage P50/P95/P99 latency and prompt size with recorded hardware, corpus, provider/model, concurrency, and sample counts. Check against a predeclared release latency budget derived from existing application/request deadlines; do not invent a universal millisecond target after measuring. Use deterministic embedding fakes for offline algorithm tests and real configured provider evidence for the embedding quality gate. Missing credentials make that external gate blocked, not passed.

Exit evidence: bounded extraction, correct membership/pinning, dynamic new-schema onboarding without router edits, reproducible retrieval quality, safe failure behavior, and verified reindex/readiness.

### D. Make installation and persistence reproducible

1. Inspect the actual migration history before choosing a fresh-install strategy. Never edit an applied migration or assume a migration appended after a failing migration can fix its prerequisites.
2. Preserve supported upgrades. Prefer a supported baseline-migration mechanism or a tested, explicit empty-database bootstrap workflow that works with the installed Flyway edition/version. Do not introduce an unsupported baseline convention merely to obtain one-pass startup.
3. If the existing Hibernate-first bootstrap remains the supported path, make it automated, empty-database-only, repeatable, guarded, and integration-tested. Normal production startup must validate schema and apply approved migrations, not use unrestricted Hibernate schema mutation. Update `fresh-database-bootstrap.md` with the exact supported sequence and recovery behavior.
4. Test fresh installation and upgrade from a representative previous release on PostgreSQL with pgvector. Assert historical schema, solver binding, run, and assignment preservation. Prove missing extension, dimension mismatch, checksum drift, and partially initialized database failures are actionable.
5. Verify a reproducible application startup, readiness, database backup/restore rehearsal in disposable infrastructure, and the recovery procedure for the changed flows. Never run destructive recovery against production.
6. Published definitions/bindings are immutable. Backfill null checksums only after canonical stored-content equality. Preserve historical corrupted definition bytes/checksums when required for replay; render safe corrected display text through versioned presentation handling and publish corrected active versions. Do not silently rewrite historical evidence.

Exit evidence: a documented executable installation/upgrade path, real pgvector queries, no historical mutation, and verified readiness/recovery behavior.

### E. Finish code organization and release gates

Organize by existing capability ownership; do not create a parallel architecture. Use the established packages or their repository equivalents:

```text
schema/catalog, compiler, registry
schema/routing/{model,index,lexical,vector,fusion,verification,service}
ai/extraction/{prompt,validation}
physics/{binding,module,solver,reference}/<topic>
physics/output
physics/validation/endcondition
physics/compatibility
service/simulation
```

- Controllers handle HTTP mapping; provider clients handle transport; compilers own compiled contracts; orchestration services coordinate typed components; repositories own bounded persistence queries.
- Extract responsibilities from large services where they still own compilation, normalization, transport, execution, and persistence together. Create an abstraction only with a real consumer and a clear responsibility. Do not create one-line forwarding layers to lower class line counts.
- Use explicit imports, immutable values, typed exceptions, actionable safe messages, and validated configuration. Avoid speculative frameworks and broad package moves unrelated to a failed requirement.
- Use indexed identity/lifecycle queries on request paths. Do not load all rows and filter in memory or repeatedly resolve the same schema within one execution.
- Preserve public API contracts through tested versioned mappers. No new public `1d`/`2d` IDs. Do not change Java numeric literals or Canvas API tokens.
- Keep catalog source deterministic and generated artifacts reproducible. Add one shared CI gate for missing bindings/output/evidence instead of many hand-maintained lists.

## 6. Efficient implementation and testing protocol

For each slice: read the relevant implementation, reproduce the gap, make the smallest complete change, run meaningful targeted tests, review the diff, and update the single evidence report. Run independent read-only inventory tasks together where practical. Keep mutations and dependent checks ordered.

Do not run the full suite after every file edit. Run it at baseline and after integration; repeat relevant gates only after new changes or unresolved failures justify it. Do not duplicate passing tests, assert private implementation details, or create tests that simply reproduce the implementation's formula.

Use parameterized contract tests driven by approved identities where appropriate, with independently specified expected behavior. Keep fake providers deterministic and network-free. Keep real PostgreSQL/pgvector tests mandatory in the release job; a skipped container test does not count as a passing migration gate.

Wire the required checks into existing CI. Inspect scripts before invoking them: do not run a mutating generation/backfill command as a check unless the workflow explicitly asserts the resulting diff.

Final commands must include the actual repository equivalents of:

```text
backend: ./mvnw clean test                  # Windows: .\mvnw.cmd clean test, or actual wrapper
backend: package the deployable artifact using the existing build configuration
react-client: npm ci                       # when dependencies need installation
react-client: npm run check
root: node scripts/generate-schema-catalog.mjs --check
root: node scripts/repair-versioned-schema-text.mjs --check
root: node scripts/check-curriculum-coverage.mjs
root: node scripts/report-topic-coverage.mjs
root: git diff --check
```

Additionally run real migration/upgrade, endpoint reindex, routing-to-persistence, historical replay, retrieval evaluation, and architecture gates. Discover their actual commands and record them. A captured repository save argument alone is insufficient proof of database persistence: include a transactional save/reload integration case.

Static checks must distinguish current runtime from explicit compatibility and inspect reachability, not merely count `JsonNode`, `switch`, or `contains` occurrences. JSON is appropriate at transport/persistence boundaries; enum strategy dispatch is valid. Ensure no schema-specific routing rules, production `PhysicsValues` use, central model dispatch, duplicate defaults, wildcard imports in changed code, active mojibake, or new dimensional public IDs remain.

Classify every warning by cause, impact, and required action. Fix warnings that indicate correctness, lifecycle, resource, or deployment risks. Do not suppress logs or relax validation to obtain a green gate.

### 6.1 Implementation examples and algorithm fixtures

The following examples explain required behavior. Adapt names and signatures to existing repository types; do not create parallel interfaces just to copy these snippets. Fixture identifiers and numeric values belong in tests, never in routing rules.

**BM25:** use an explicitly documented variant, for example:

```text
idf(t) = ln(1 + (N - df(t) + 0.5) / (df(t) + 0.5))
score(q,d) = sum over distinct query terms t:
  idf(t) * tf(t,d) * (k1 + 1)
  / (tf(t,d) + k1 * (1 - b + b * length(d) / averageLength))
```

If the existing implementation intentionally uses query-term frequency, document and test that variant rather than silently changing it. Define empty-corpus, empty-query, zero-match, and token-limit behavior. Never divide by zero for an empty index.

Hand-calculated fixture: documents A=`alpha alpha`, B=`beta beta`; query=`alpha`; N=2, df=1, averageLength=2, k1=1.2, b=0.75. A must score `1.375 * ln(2)` (approximately 0.9530773733); B scores zero. The expected expression must be independent of the production scoring helper.

**Cosine:** vectors `[1,0]`, `[0,1]`, and `[-1,0]` have similarities 1, 0, and -1 to query `[1,0]`. Test actual pgvector ordering separately from fake retrieval. Reject zero-norm vectors, non-finite components, and dimension mismatch before cosine queries; cosine is undefined for a zero vector. Do not clamp negative similarity to positive evidence without an explicit documented policy.

**RRF:** use one-based ranks and deduplicate each retriever's identities. With k=60, lexical `[A,B]` and vector `[B,A,C]`, A and B both score `1/61 + 1/62`, while C scores `1/63`. Resolve A/B deterministically by the declared identity order. A missing retriever contribution is zero. Never normalize by the number of retrievers in which a candidate happens to appear.

**Typed execution:** the essential invariant is that persistence receives only accepted output. The following is illustrative Java-style pseudocode, not an instruction to add these exact classes:

```java
var context = preparation.prepare(pinnedSpecification); // compile quantities once
var module = modules.require(context.binding());
var parameters = module.bind(context.quantities());
var rawOutput = module.solve(parameters, context.clock());
outputValidator.requireValid(context.outputContract(), rawOutput);
referenceValidator.requireAgreement(context, parameters, rawOutput);
var finalOutput = endConditions.resolveAndTrim(context, rawOutput);
outputValidator.requireValid(context.outputContract(), finalOutput);
referenceValidator.requireAgreement(context, parameters, finalOutput);
return snapshots.persistAccepted(context, finalOutput);
```

Adapt ordering to existing bounded horizon expansion and reference sampling semantics, but validate any newly interpolated/trimmed values as required by the compiled contract. Reuse parameters/context across expansion steps. Do not introduce repeated canonicalization or unrestricted additional solves.

**Extensibility proof:** in test fixtures, register a synthetic schema version using an existing typed module and metadata unknown to the router. Approve/index it through the real lifecycle flow, then prove retrieval can find it without editing router/prompt/dispatch code. Retire it and prove new requests exclude it while pinned replay remains valid. This tests onboarding; it does not replace held-out retrieval evaluation.

### 6.2 Failure diagnosis and correction protocol

For every failure, follow this loop:

1. Capture the smallest reproducible input, pinned identities, expected result, actual result, and safe error code. Keep sensitive examples out of logs and committed fixtures.
2. Determine which boundary is wrong using evidence: metadata, tokenizer/retrieval, extraction, canonicalization, binder, numerical solver, reference, output validator, end condition, persistence, or environment.
3. Verify the expected behavior independently before changing code. A disagreement between numerical and reference results does not prove which is correct.
4. Fix the responsible layer and add or strengthen a regression test for the externally observable defect. Published contract changes require a new version; implementation fixes must preserve the documented replay contract.
5. Rerun the reproducer, affected contract tests, and adjacent integration path. Broaden tests only when the change crosses more boundaries.
6. Record the cause, correction, and passing evidence. If a repeated attempt produces the same failure, investigate the assumption or environment instead of rerunning the same command or widening tolerances.

| Observed failure | Required investigation and correction | Forbidden shortcut |
|---|---|---|
| Correct schema missing from candidates | Inspect approved metadata, tokenization, index generation, vector identity, and ranks; fix source/index or generic algorithm | Add a phrase-to-schema branch |
| AI selects an outside schema or emits numeric strings | Reject at strict boundary; use only an existing bounded retry with the same pinned contracts | Coerce silently or send the full catalog |
| Correct raw measurement becomes wrong canonical value | Check catalog unit conversion, dimension, alias collision, and ingress normalization | Add a solver-local unit/default patch |
| Numerical/reference disagreement | Check independent golden derivation, assumptions, units, integration error, and tolerance semantics | Copy one formula into the other or relax tolerance |
| Output has wrong kind, unit, key, or shape | Compare compiled declaration with intended physics and typed producer; version incorrect published metadata | Drop unknown values or reinterpret scalars as repeated series |
| pgvector/reindex/provider failure | Diagnose extension/configuration/model/generation/deadline; recover through supported bounded policy | Random embeddings, wrong-model reuse, or unannounced fallback |
| Historical replay changes | Compare stored identity, binding, defaults, output mapper, and snapshot; repair versioned compatibility | Rewrite old snapshots or route to the latest version |

### 6.3 Output acceptance and review gates

Output acceptance is enforced by backend code and executable checks. An AI self-review or a well-formed JSON response is not approval of physical correctness.

| Boundary | Accept only when | Rejection behavior |
|---|---|---|
| Extraction | Strict JSON, pinned candidate membership, raw numeric quantities, allowed fields, and valid ambiguity structure | Safe contract error or bounded retry; unresolved ambiguity cannot start simulation |
| Canonical input | Keys resolved once, dimensions/units valid, defaults materialized, required data present, domain constraints satisfied | Typed canonical/domain error before numerical execution |
| Module output | Exactly allowed keys, required outputs present, declared kinds/units/shapes, finite values, valid time/axes, bounded size | Typed output error before successful run persistence |
| Physical validation | Independent reference/probes and declared invariants meet per-output comparison rules | Failed validation; never mark the run accepted |
| End condition | Declared source/event, deterministic termination, valid trimmed shapes and interpolated values | Typed error or documented bounded failure; no silent success |
| Persisted result | Snapshot pins schema/binding/contract identities and save/reload preserves values and shapes | Transactional failure; no partially accepted snapshot |
| Release | Every original criterion has implementation plus final-revision executable evidence | NOT PRODUCTION READY |

For continuous numeric comparison, use the compiled, versioned tolerance convention. If none is defined yet, define and test `abs(actual - expected) <= absoluteTolerance + relativeTolerance * abs(expected)`, with finite nonnegative tolerances and compatible units. Do not silently change existing published comparison semantics. Exact/discrete comparison must not use floating tolerance. Vector/field comparison must specify componentwise or norm-based behavior, alignment, and aggregation explicitly; an average error must not hide a failed required probe.

Hand-calculated tolerance fixture for the convention above: expected=10, absoluteTolerance=0.01, relativeTolerance=0.001 gives a bound of 0.02; actual=10.015 passes, actual=10.03 fails. Include zero-expected, boundary, and non-finite cases. Domain invariants and golden expectations remain model-specific test evidence, not router rules.

Use safe diagnostics containing schema ID/version, model ID, output key/index, expected, actual, comparison mode, and tolerance where applicable. Do not emit empty `expected=` messages. Invalid output may produce a failure-status record under the existing API, but must never be exposed or stored as an accepted simulation result.

Before closing a slice, review the diff for correct ownership, versioning, absence of schema-specific routing logic, meaningful independent tests, and current evidence. Human release approval, if the deployment process requires it, is a separate final action; do not add manual approval to every normal simulation request.

## 7. Mandatory success criteria

All original section-23 requirements must have complete evidence, plus these concrete release checks:

- [ ] Every active identity has a valid compiled schema, typed numerical/reference binding, complete output contract, and executable model evidence.
- [ ] New schema metadata enters retrieval after approval/index without editing central code; new algorithms require only their module, binding, schema, and evidence.
- [ ] Retrieval uses real BM25, cosine pgvector, deterministic RRF, and generic contract verification; held-out quality and declared performance gates pass.
- [ ] AI receives only bounded pinned contracts; strict membership, numeric/duplicate validation, retries, and ambiguity preservation pass.
- [ ] Current execution compiles canonical quantities once; solver/reference APIs accept typed parameters and produce validated typed output.
- [ ] All supported output shapes and end conditions validate, trim, persist, reload, and replay correctly.
- [ ] Every model has independent expected results and executable golden/boundary/domain/invariant/fault-detection evidence appropriate to its physics.
- [ ] Legacy behavior is confined to authorized versioned compatibility with persisted replay tests; active requests cannot fall back to it.
- [ ] Schema/binding history and assignment/run snapshots remain intact; published version drift and unsafe checksum backfill are rejected.
- [ ] Fresh installation, supported upgrade, pgvector operation, failure recovery, and readiness pass using the documented deployment path.
- [ ] Embedding failure handling and authorized idempotent reindex pass, including concurrent lifecycle changes and partial failure.
- [ ] Current source organization has clear ownership; no duplicated contract authority or growing schema/model dispatch remains.
- [ ] Backend artifact, complete backend tests, frontend checks, scene contracts, catalog drift, curriculum/topic non-regression, and CI gates pass on the final revision.
- [ ] No test is skipped to obtain readiness; external gates have actual evidence; warnings and remaining operational constraints are explicit.
- [ ] Every original acceptance checkbox maps to a file and executable verification; there are no unresolved mandatory PARTIAL/NOT DONE/BLOCKED items.

Internal curriculum coverage must remain intact. Do not claim complete official textbook/program coverage without the source-to-requirement mapping required by `docs/curriculum/official-program-gap-audit.md`. Do not add unrelated curriculum scope to disguise a release blocker.

## 8. Explicit prohibitions

Do not:

- Rewrite working retrieval components solely for stylistic consistency.
- Hardcode phrases, topic names, schema IDs, per-schema thresholds, or expected benchmark answers into selection logic.
- Move physics equations to JSON, prompts, embedding text, frontend code, or a dynamic expression engine.
- Let AI generate executable solver code, invent identities, compute missing measurements, or become the correctness oracle.
- Send the full catalog after a failure, broaden pinned candidates during retry, or silently substitute an embedding model.
- Add cache/HNSW/distributed infrastructure without evidence that it solves a measured problem.
- Mutate published schema versions, edit applied migrations, erase snapshots, or discard historical behavior to reduce test work.
- Generate golden values with the code being validated or use shared numerical/reference formulas as independent proof.
- Weaken thresholds, disable guards, remove failing assertions, reclassify supported models, or skip integration tests to declare completion.
- Perform repository-wide formatting, dependency upgrades, unrelated cleanup, speculative abstractions, or duplicate documentation.
- Equate passing build/test counts with production readiness, or claim a percentage without a defined evidence-based denominator.

## 9. Final delivery

Update the existing production report with the verified baseline, changes by capability, migration/bootstrap behavior, compatibility boundaries, exact final commands/results, retrieval benchmark, per-model evidence references, warning classifications, and every original acceptance checkbox.

Use `PRODUCTION ACCEPTANCE PASSED` only when every mandatory gate has current evidence, and state the tested environment and release scope. This means the artifact satisfies these acceptance criteria; it does not mean it has been deployed or guarantees correctness for every possible input.

Otherwise use `NOT PRODUCTION READY`, name the precise remaining requirements and external blockers, and continue all implementation work that can still be completed. Do not create a new definition of production readiness to fit the result.
