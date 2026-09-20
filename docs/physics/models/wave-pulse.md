# `wave_pulse` — xung truyền Gaussian trên dây

- Model version: `1.0.0`
- Formula ID: `wave-pulse.gaussian.v1`
- Solver/reference: `wave_pulse_solver` / `wave_pulse_reference`

The field is an ideal, nondispersive right-moving pulse:

```text
q = (x - x0 - c t) / σ
u(x,t) = A exp(-q²)
u_t = 2 A (c/σ) q exp(-q²)
u_tt = A (c/σ)² (4q² - 2) exp(-q²)
```

`A`, `c`, and `σ` are SI inputs; `x0` and the probe/domain are optional. The
Gaussian is a mathematical pulse, not a claim that every real string has an
infinite tail or no reflection. The field uses the same versioned scalar-field contract
as the periodic-string pilot and remains bounded by the scalar-field resource
limits.
