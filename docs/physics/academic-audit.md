# PhysLive physics academic audit

Audit date: 2026-09-20. The audit checks the numerical solver against an
independent reference evaluator, SI units, sign conventions, limiting cases,
and the documented domain of each model. A passing test is evidence of an
implementation invariant; it is not a teacher/curriculum approval.

## Reviewed contracts

| Family | Governing relation | Explicit scope/limits |
| --- | --- | --- |
| Kinematics | `x=x₀+v₀t+½at²`, projectile components | constant acceleration; projectile has no drag |
| Dynamics | Newton II with Coulomb friction; elastic collision; `x=A cos(ωt+φ)` | point-mass model; collision is frictionless and elastic; spring is SHM |
| RC circuit | `V_C=V(1−e^(−t/RC))`, `I=(V/R)e^(−t/RC)` | ideal first-order RC step response |
| Waves | `u=A cos(kx−ωt+φ)`, Gaussian pulse, reflection/superposition/standing wave | ideal linear medium; boundary and resonance assumptions are declared per model |
| Sound | `p=P₀ cos(kx−ωt+φ)` | scalar pressure wave; no attenuation, room geometry, or particle-velocity coupling |
| Ideal gas | `pV=nRT`, `W=nRT ln(V/V₀)` | quasistatic reversible isothermal path; `V(t)>0` |
| Calorimetry | `T_f=ΣmcT/Σmc`, `ΣQ=0` | isolated two-body mixture; constant `c`; no phase change |
| First law | `ΔU=Q−W` | `W` is work done by system; balance checkpoint, not a path solver |
| Optics | `1/f=1/d_o+1/d_i`, `M=−d_i/d_o`; Snell `n₁sinθ₁=n₂sinθ₂` | paraxial thin lens; planar interface; TIR is flagged |
| Induction | `Φ=B_nA cosθ`, `ε=−N dΦ/dt` | fixed coil, uniform signed normal field component, linear `B_n(t)` |
| AC RLC | `X_L=ωL`, `X_C=1/(ωC)`, `Z`, RMS power factor and `P=I²R` | steady-state series phasor model; no transient |
| Modern physics | `K_max=max(0,hf−Φ)`, `E=Δmc²`, `N=N₀e^(−λt)`, `A=λN` | photoemission threshold is explicit; nuclear model is mass-defect conversion; decay is expectation value |
| Fields/data | `E=k|q|/r²`, `V=kq/r`, `F=|q|vB sinθ`; interval uncertainty | vacuum point-charge scalar probe; Lorentz force magnitude only; relative uncertainty undefined at `x=0` |
| Thermal/electric extensions | `T_K=T_C+273.15`, `ΔL=αL₀ΔT`, `V=IR`, `R_s=ΣR`, `1/R_p=Σ1/R`, `Q=CV`, `U=½CV²` | ideal materials, lumped DC networks and ideal capacitor; no temperature-dependent resistivity |
| AC/optical/modern extensions | `u=U₀sin(ωt+φ)`, transformer ratios, two-source interference, single-slit minima, Malus, Rydberg, inverse-square dose | steady-state/ideal transformer; paraxial optical paths; hydrogen-like spectrum; teaching dose-rate model |
| Optical instruments/adiabatic gas | magnifier, compound microscope, astronomical telescope; `PV^γ=const` | paraxial thin lenses and reversible adiabatic path; aberrations, real-gas losses and transient heat transfer excluded |
| Water surface | `u=A[cos(kr_1-ωt)+cos(kr_2-ωt)]` | two coherent point sources on a bounded square grid; no tank boundaries, dispersion or nonlinear breaking |
| Mechanics foundations | `W=Fs cos(theta)`, `Delta K`, `a_c=v^2/r`, `F=-kx`, Newtonian gravity, `p=rho gh`, Archimedes | constant-force work-energy; uniform circular motion; linear elasticity; circular-orbit and quiescent-fluid idealizations |
| Applications | AM carrier sidebands, Shockley diode, pulse-echo depth `d=vt/2` | ideal modulation, fixed-temperature diode and single-interface ultrasound; no antenna, circuit parasitics or tissue attenuation |

## Correctness controls added

- Canonical schema keys are resolved before the solver boundary; solvers do not
  infer aliases.
- Reference solvers independently recompute every validation probe.
- Schema validation now supports integer quantities and cross-field
  `sameUnitAs` checks (used for measurement uncertainty).
- Boundary cases are tested: zero volume rate, zero charge, zero object height,
  `λ=0`, below-threshold photons, zero measured value, and non-integer coil
  turns.
- `emissionOccurs` and `relativeUncertaintyDefined` prevent a numeric zero
  sentinel from being misread as a physical assertion.

## Source basis

The formula review uses OpenStax *University Physics*: [wave mathematics](https://openstax.org/books/university-physics-volume-1/pages/16-2-mathematics-of-waves),
[work/internal energy](https://openstax.org/books/university-physics-volume-2/pages/3-2-work-heat-and-internal-energy),
[first law](https://openstax.org/books/university-physics-volume-2/pages/3-3-first-law-of-thermodynamics),
[Faraday's law](https://openstax.org/books/university-physics-volume-2/pages/13-1-faradays-law),
[thin lenses](https://openstax.org/books/university-physics-volume-3/pages/2-4-thin-lenses),
[photoelectric effect](https://openstax.org/books/university-physics-volume-3/pages/6-2-photoelectric-effect), and
[radioactive decay](https://openstax.org/books/university-physics-volume-3/pages/10-3-radioactive-decay).

Remaining academic gaps are intentionally not hidden: full vector electric or
magnetic fields, transient heat-transfer kinetics, phase-change models and
stochastic detector counts require additional state variables and independent
contracts before implementation. All 73 inventory
rows now have a simulation mapping; source edition/locator and teacher review
remain open.
