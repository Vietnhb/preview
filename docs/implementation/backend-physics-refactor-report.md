# Backend physics refactor report

This report records the changes implemented from `PROMPT_REFACTOR_BACKEND_PHYSICS_PRODUCTION.md` in the current worktree.

## Implemented

- Added immutable `CanonicalQuantityBag`, `CanonicalQuantity`, `PhysicalChecks`, and an explicit `LegacySpecificationAdapter`.
- Added strict pre-binding AI JSON validation, including numeric-type checks, unknown-field rejection, duplicate quantity/ambiguity detection, limits, and end-condition shape checks.
- Canonical schema resolution now fails unknown keys instead of returning a raw alias; schema quantity/adjustment/visualization and checkpoint contracts are validated.
- Added immutable `CompiledSchema`/`SchemaCompiler` snapshots and schema-definition checksum persistence with catalog drift detection (`V7__add_schema_definition_checksum.sql`).
- Changed numerical/reference registries to immutable maps with duplicate-ID failure.
- Removed wildcard imports from physics production/reference code and introduced the shared `SimulationTimeline` runtime utility; topic solvers no longer depend on `TemperatureScaleSolver.staticTime()`.
- Added topic-owned application solver/reference modules for circuits, waves, modern physics, and practical/data. The historical `ApplicationsSolver`/reference classes are now route-only compatibility adapters; they contain no physics switch/formula and are not the target for new schema bindings.
- Removed the optical reference solver's direct dependency on the numerical solver.
- Added output timeline/series/scalar-field contract checks before persistence and per-output absolute/relative/exact/discrete validation reporting.
- End-condition trimming now preserves scalar-field axes/shape and interpolates its final time row instead of silently dropping the field payload.
- Removed end-condition event discovery by `key.contains(...)`; event markers require an explicit schema-bound `markerQuantity`, and operators use an allow-listed enum.
- Corrected damped forced oscillation dimensions (`γ=c/(2m)`), added non-unit-mass coverage, and made the reference oracle calculate independently.
- Migrated the highest-risk dynamics/circuit binders (Hooke, hydrostatics, damped oscillator, AC waveform) to canonical bags while retaining a versioned compatibility overload.
- Replaced repeated physical constants with `PhysicalConstants` where touched and preserved the existing project gravity default for behavior compatibility.
- Replaced the known mojibake readiness prompt with UTF-8 Vietnamese text.

## Verification

`backend/.\mvnw.cmd -q test` passes after the changes. Remaining repository-wide checks must still be run before release.

## Explicit remaining work

The prompt's full production gate is not claimed complete yet: the remaining parameter records/solvers still need typed-binder migration, multi-topic application solvers still need package/module extraction, `ProblemService` and `EndConditionResolver` still need responsibility splitting, and frontend/scene/curriculum checks plus full replay/DB integration evidence remain to be run. No published schema data was destructively changed.
