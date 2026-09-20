# `wave_reflection` — Gaussian pulse reflection

- Model version: `1.0.0`
- Formula ID: `wave-reflection.image-pulse.v1`
- Solver/reference: `wave_reflection_solver` / `wave_reflection_reference`

The ideal boundary is represented with an image pulse. For a boundary at `L`,
the incident and reflected coordinates are

```text
q_i = (x - x0 - c t) / sigma
q_r = (2L - x - x0 - c t) / sigma
u(x,t) = A exp(-q_i^2) + R A exp(-q_r^2)
R = -1 (fixed boundary), +1 (free boundary)
```

The same time derivatives are evaluated analytically for the probe series.
The field is bounded to `[domain_start, boundary_position]`; this is an ideal
lossless, nondispersive image solution, not a model of finite impedance or
energy loss at a real boundary.
