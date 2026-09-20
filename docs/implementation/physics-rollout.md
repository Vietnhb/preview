# Physics rollout checkpoint

## Checkpoint A — inventory

- Baseline: frontend 5 tests pass; backend 12 tests pass before pilot.
- Existing catalog: 7 schema/model definitions, mostly kinematics/dynamics/RC.
- Official curriculum and textbook matrix: not closed; see `docs/curriculum/sources.md`.

## Checkpoint B — wave pilot (verified in this checkout)

Implemented in this checkpoint:

- `ScalarField` version 1 validates scalar coordinates, shape, finite values,
  monotonic time, sampling and bounded resource usage.
- `StringWaveSolver` and independent `StringWaveReferenceSolver` implement
  periodic right-moving waves and a delayed `source_started` mode.
- Run/API payloads carry `scalarFields`; the frontend normalizes and bilinearly
  samples the same field for the wave renderer and probe.
- `waveField` is a validated scene primitive; missing field/binding errors are
  visible instead of silently becoming zero.
- Formula derivation and model limits are recorded in
  `docs/physics/models/string-wave.md`; the contract decision is in ADR-0001.
- Verification result at the current checkpoint: backend 15 tests pass, frontend
  architecture/lint/8 tests/build pass, runtime and canvas lifecycle checks pass,
  and the curriculum checker reports 14 inventory rows with 0 approved for
  release (the pilot is tested, not academically approved).

## Verification

Run from `physLive_preview/`:

```powershell
cd backend; .\mvnw.cmd test
cd ..\react-client; npm run check
node scripts/check-curriculum-coverage.mjs
```

The pilot is not a full-program release. Continue with wave-family gaps and
then the curriculum matrix; do not label the product `100%` until all rows are
source-closed and `approved`.

## Checkpoint C — first wave-family expansion

Implemented and tested after the pilot:

- `wave_pulse`: bounded Gaussian travelling pulse with independent
  analytical reference evaluator and the shared field/probe output.
- `standing_wave`: counter-propagating-wave superposition with node/antinode
  field behavior and independent reference evaluator.
- Both schemas use the existing validated `waveField` primitive and replay
  object-map adapter; no fake particle is created per spatial sample.

Remaining wave gaps are explicit: water-surface fields and sound. The
standing-wave model intentionally does not claim fixed-end resonance until
mode/boundary constraints and corresponding tests are added.

## Checkpoint E — reflection and superposition

Implemented and tested two additional wave capabilities:

- `wave_reflection` uses an independent image-pulse reference for fixed and
  free boundaries, exposing the boundary coefficient in the scalar-field
  metadata.
- `wave_superposition` sums two coherent right-moving waves and verifies
  phase cancellation against an independent probe evaluator.
- `sound_wave` carries acoustic pressure in pascals through the same field
  contract, with an independent pressure reference evaluator.

Both models reuse the bounded scalar-field contract and existing `waveField`
scene primitive. They do not claim finite-impedance reflection, nonlinear
coupling, or water surfaces.

## Checkpoint F — first thermal model

`ideal_gas_isothermal` adds a timeseries model for `pV=nRT`, a configured
volume-rate process and analytical work. It has its own parameter record,
independent reference solver, graph-based scene mapping and Boyle-law
regression test. The curriculum matrix now explicitly lists thermal, optics,
electromagnetism, modern-physics and practical/data outcomes that remain
missing until their contracts and evidence are implemented.

The same checkpoint now includes `thin_lens_imaging` (thin-lens equation and
magnification) and `radioactive_decay` (exponential half-life/activity), each
with an independent reference solver and invariant test.

## Checkpoint G — calorimetry, thermodynamics, induction, AC and quantum

- Ideal calorimetry mixing and the first-law energy balance are available with
  explicit sign conventions and invariant tests.
- Snell refraction, Faraday induction and steady-state series AC RLC are
  registered as independent formula models.
- Photoelectric threshold/kinetic energy and mass-defect energy are available
  with canonical SI fields and reference solvers.
- Point-charge electric-field/potential probes, Lorentz magnetic force and
  basic measurement uncertainty intervals are now registered and tested.

## Checkpoint D — canonical quantity boundary

The specification boundary now canonicalizes quantity names before a model is
executed. AI extraction, schema normalization, problem writes, ambiguity
resolution, and readiness serialization map aliases/symbols to the schema's
canonical `key`. `PhysicsValues` and every solver/reference solver then read
exact canonical keys only; aliases are no longer dispatch logic in the physics
layer. Model-specific parameter records still own defaults, physical
validation, and formulas. Regression coverage includes rejection of alias
dispatch inside `PhysicsValues` and canonicalization of schema quantities.

## Checkpoint H - THPT family expansion and academic audit

The schema catalog now contains 53 versioned models. In addition to the wave,
thermal and field families above, it includes temperature conversion, linear
expansion, Ohm/DC resistor networks, capacitors, sinusoidal AC, ideal
transformers, optical interference/diffraction/polarization, three paraxial
optical instruments (magnifier, compound microscope, astronomical telescope),
hydrogen spectra, inverse-square radiation safety, reversible adiabatic gas and
a bounded two-source water-surface field. It now also covers work-energy and
power, circular motion, Hooke deformation, gravity/orbit, hydrostatics, radio
communication, semiconductor diode behavior, pulse-echo ultrasound and
isobaric/isochoric ideal-gas paths.
Each new model has a canonical parameter record, validation, numerical solver,
independent reference solver and a golden test in
`CurriculumMissingFamiliesTest`.

The coverage matrix is 73 rows over the 68-lesson bootstrap catalog: the
checker confirms 68/68 catalog lessons are mapped and all 73 rows are
`tested`. All rows have a simulation mapping
(100%); release/program coverage remains 0% until
authoritative source locators and subject-matter reviewers move rows to
`approved`.

## Checkpoint I - schema-authored visual engine

The frontend accepts a validated `vectorScene` scene primitive with SVG-style
paths, ellipses, rectangles, text, nested transforms and safe numeric
expressions bound to solver output. Its fallback compiler derives scalar and
lane layouts from declared actors rather than schema or lesson IDs. Series-only
schemas receive responsive chart panels. Backend draft approval and bootstrap,
plus the frontend `check:scenes` gate, reject unsupported primitives, effects,
bindings, malformed paths and excessive scene resources. All 53 catalog schemas
pass the gate; optical-instrument schema 1.1 uses declarative vector diagrams.
