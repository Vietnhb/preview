# `wave_superposition` — two-wave superposition

- Model version: `1.0.0`
- Formula ID: `wave-superposition.coherent-pair.v1`
- Solver/reference: `wave_superposition_solver` / `wave_superposition_reference`

The model sums two coherent right-moving harmonic waves with a shared
frequency and speed:

```text
u(x,t) = A1 cos(kx - omega t + phi1)
       + A2 cos(kx - omega t + phi2)
k = omega / c, omega = 2 pi f
```

Velocity and acceleration are the exact time derivatives of this sum. The
model demonstrates linear superposition and phase cancellation; it does not
claim nonlinear coupling, dispersion, or independent source geometry.
