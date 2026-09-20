# `standing_wave` — giao thoa sóng dừng

- Model version: `1.0.0`
- Formula ID: `standing-wave.counter-propagating.v1`
- Solver/reference: `standing_wave_solver` / `standing_wave_reference`

The model represents the superposition of two equal counter-propagating waves:

```text
k = 2π f / c
ω = 2π f
u(x,t) = A sin(k(x - x0)) cos(ωt + φ)
u_t(x,t) = -A ω sin(k(x - x0)) sin(ωt + φ)
u_tt(x,t) = -ω² u(x,t)
```

This checkpoint exposes nodes from the spatial sine factor and a shared probe
series. It does not yet enforce a resonant mode number or fixed/free-end
boundary condition; those are required before claiming a particular textbook
experiment such as a string with two fixed ends. Reflection and multi-source
superposition remain separate gaps.
