# `string_wave` — sóng ngang điều hòa trên dây căng

- Model version: `1.0.0`
- Formula ID: `string-wave.traveling.v1`
- Solver ID: `string_wave_solver`
- Reference evaluator ID: `string_wave_reference`
- Status: `tested` only after the automated fixture and reviewer checks pass; this document is **not** an academic approval.

## Nguồn và phạm vi

The governing form, phase convention, and the relationships between `k`, `ω`,
`f`, `λ`, and propagation speed follow OpenStax *University Physics Volume 1*,
[§16.2 Mathematics of Waves](https://openstax.org/books/university-physics-volume-1/pages/16-2-mathematics-of-waves).
For a taut string, `c = sqrt(F / μ)` is documented in
[§16.3 Wave Speed on a Stretched String](https://openstax.org/books/university-physics-volume-1/pages/16-3-wave-speed-on-a-stretched-string).
Sources were checked on 2026-09-20. Curriculum mapping and teacher approval are
tracked separately in `docs/curriculum/coverage.csv`.

This is an ideal, small-amplitude transverse wave on a uniform string. It does
not model damping, dispersion, reflection, nonlinear geometry, air coupling,
or a real vibration actuator. It must not be used as a hydrodynamics, sound, or
electromagnetic-wave model.

## Coordinates, symbols, and SI units

| Symbol | Meaning | SI unit | Convention / domain |
| --- | --- | --- | --- |
| `x` | equilibrium coordinate along the string | m | increases in the propagation direction |
| `u(x,t)` | transverse displacement | m | positive is drawn upward |
| `t` | model time | s | `t >= 0` |
| `A` | displacement amplitude | m | `A >= 0` and small relative to `λ` |
| `f` | frequency | Hz | `f > 0` |
| `c` | wave propagation speed | m/s | `c > 0`; not particle speed |
| `φ` | phase at `x=0,t=0` | rad | finite |
| `λ` | wavelength | m | derived as `c / f` |
| `k` | wave number | rad/m | derived as `2π / λ` |
| `ω` | angular frequency | rad/s | derived as `2π f` |
| `F` | constant tension magnitude, optional alternate input | N | if used with `μ`, `c` is derived |
| `μ` | linear mass density, optional alternate input | kg/m | if used with `F`, `c` is derived |

The pilot implementation accepts the independent set `(A, f, c, φ)`. The
textbook relation `c = sqrt(F / μ)` is recorded as a future input adapter; the
current schema does not expose `F` and `μ`, so they must not be entered as
unvalidated extra quantities.

## Governing equation and derivation

For an infinitesimal string segment `dx`, with small slope and constant tension
magnitude `F`, the net transverse force is

```text
F_y ≈ F [u_x(x + dx,t) − u_x(x,t)] ≈ F u_xx dx.
μ dx u_tt = F u_xx dx
u_tt = c² u_xx,    c² = F / μ.
```

For a periodic right-moving wave, the evaluated field is

```text
θ = kx − ωt + φ
u(x,t) = A cos(θ)
ω = 2πf,  λ = c/f,  k = 2π/λ,  c = ω/k = fλ
v_particle(x,t) = ∂u/∂t = Aω sin(θ)
a_particle(x,t) = ∂²u/∂t² = −ω²u(x,t)
```

Substitution gives `u_tt = −ω²u` and `u_xx = −k²u`; therefore the wave
equation requires `ω² = c²k²`. A constant phase has `dx/dt = ω/k = +c`, so the
minus sign before `ωt` is the positive-`x` convention. A marker on the canvas
therefore remains at a fixed `x` and moves only in `u`; it never travels with a
crest.

## Initial-source mode

`periodic` represents a wave already established over the represented domain;
its initial string state is generally not at rest. `source_started` represents
a source switched on at `t = 0` on a semi-infinite string:

```text
τ = t − x/c
u(x,t) = 0                         for τ < 0
u(x,t) = A r(τ) cos(φ − ωτ)        for τ >= 0
```

`r(τ)` is a declared finite ramp used to avoid presenting an instantaneous
whole-string disturbance. The front `x = ct` remains visibly undisturbed ahead
of the source. This source model has no reflected wave; fixed/free-end
reflections and standing waves require distinct model versions and boundary
conditions.

## Output contract and precision

The solver stores a versioned `scalarFields.displacement` payload: one spatial
axis (`x`, m), a monotonic time axis (s), shape `[timeCount, spaceCount]`,
finite `u` samples (m), sampling metadata, interpolation policy, and boundary
description. It also stores same-run probe series for displacement, particle
velocity, and particle acceleration. Rendering may use `Float32`, but grading
and reference checks use the original `double` solver values.

## Independent checks

The reference evaluator recomputes a probe from the equations above rather than
interpolating the sampled field. The mandatory golden fixture is:

```text
A = 0.02 m, f = 2 Hz, c = 4 m/s, φ = 0
λ = 2 m, ω = 4π rad/s, k = π rad/m
x = 0.5 m, t = 0: u = 0; v_particle = 0.08π m/s
x = 0 m,   t = 0: u = 0.02 m; a_particle = −0.32π² m/s²
```

Regression tests also assert zero amplitude, phase periodicity, opposite phase
at `λ/2`, rightward crest propagation, and the undisturbed source front. The
numeric tolerance is defined in the schema/test fixture, not inferred from
screen pixels.
