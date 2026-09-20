# ADR 0007: Typed scalar outputs and legacy serialization

## Status

Accepted for the incremental runtime migration.

## Context

Some physics models produce discrete scalar results, such as force magnitude or absorbed dose, rather than sampled time series. Treating every scalar as a repeated series loses its actual shape and complicates validation, persistence, and scene binding. The existing response API still exposes legacy series-shaped values, so an incremental compatibility boundary is required.

## Decision

- `SolverOutput` carries immutable named scalar values independently from time-series samples.
- Schema output definitions identify output key, kind, unit, and required status. The typed output validator checks definitions when they are available.
- Simulation persistence stores scalar outputs separately. The response contract exposes `scalarOutputs` as a named numeric object.
- A versioned compatibility mapper may expose a scalar as a one-element array only when adapting to the legacy response `values` field. It must not duplicate the scalar into a time series inside the runtime or persisted snapshot.
- Existing constructors and historical payloads remain readable while models migrate. New model bindings should declare and produce the typed output kind directly.

## Consequences and remaining work

This preserves scalar shape through solver execution and persistence for migrated models while keeping the current client response usable. It introduces an additive response field and a new persistence snapshot field. Existing clients may continue using the legacy `values` adapter.

The migration is incomplete: only selected typed modules declare first-class scalar outputs; compiler validation does not yet verify every output unit against the shared unit catalog, and legacy solvers still return map-shaped output. Full API/replay compatibility and all-model output validation remain required before production readiness.

## Verification

- `LegacyScalarOutputSeriesAdapterTest` verifies the legacy one-element projection.
- `SimulationServiceScalarPersistenceTest` verifies persistence keeps scalar outputs separate.
- `OutputContractValidatorTest` verifies typed scalar and other output frame structure.
- `SchemaRoutingSimulationPersistenceIntegrationTest` verifies a routed typed simulation reaches persisted output.
