# ADR-0001: Versioned scalar-field output for wave simulations

Status: accepted for the wave families (2026-09-20)

## Context

The legacy run contract has parallel time series (`time`, `positions`,
`velocities`, `accelerations`, and `values`). It is sufficient for a finite
set of bodies but cannot truthfully represent `u(x,t)` or `u(x,y,t)` without
treating every spatial sample as a fake actor or unrelated series. A renderer,
probe, chart, assignment snapshot and server validation must read the same
physical state.

## Decision

Add an optional, versioned `scalarFields` map to `SolverOutput`, persisted run
JSON, API responses and the client runtime. Version 1 supports a primary
spatial axis and an optional second axis. A single-axis entry uses:

```json
{
  "version": 1,
  "type": "scalarField",
  "physicalDimension": 1,
  "axes": [{ "key": "x", "unit": "m", "coordinates": [0, 0.05] }],
  "shape": [201, 161],
  "time": [0, 0.05],
  "values": [[0.02, 0.01975]],
  "valueUnit": "m",
  "timeUnit": "s",
  "sampling": { "spaceStep": 0.05, "timeStep": 0.05 },
  "interpolation": "linear",
  "boundary": "open"
}
```

A plane entry adds a y axis and shape `[timeCount, xCount, yCount]`; each time
row is flattened in x-major order (`xIndex * yCount + yIndex`). The water
surface solver uses this form for two coherent point sources. All axes and time
samples are finite and strictly monotonic; field dimensions, sample count and
serialized cell budget are checked before a run is accepted. A field keeps an
explicit physical `valueUnit` (for example `m` for displacement or `Pa` for
acoustic pressure); the contract does not infer units from the renderer.

Probes use the same field and linear interpolation rather than an unrelated
canvas approximation. The numerical solver keeps `double` values; the client
may convert a rendering cache to `Float32Array` only after validation.

## Alternatives considered

1. One time series per spatial point. Rejected: it misrepresents a field as
   many actors, makes binding ambiguous and scales poorly.
2. Ship arbitrary executable evaluator code. Rejected: unsafe for AI-generated
   content and not suitable for deterministic grading.
3. Parametric evaluator only. Deferred: it requires a versioned allowlisted
   evaluator contract and parity testing on both client and server.
4. Unbounded plane tensors. Rejected: version 1 enforces `MAX_CELLS` and bounded
   grid dimensions; chunking remains a future optimization for larger scenes.

## Consequences

- Existing timeseries-only runs remain valid: `scalarFields` is optional and
  legacy output receives no fake field.
- The water-wave model has an explicit axis/shape contract, deterministic
  replay samples, a reference evaluator and a renderer heatmap/cross-section.
- Rendering primitives bind to a field ID and fail visibly if it is missing;
  they do not replace missing data with zero.
- Run snapshots pin the scalar-field contract/version and scene visualization
  used at creation so a later template does not change replay.
