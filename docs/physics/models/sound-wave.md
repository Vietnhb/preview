# `sound_wave` — acoustic pressure field

- Model version: `1.0.0`
- Formula ID: `sound-wave.pressure.v1`
- Solver/reference: `sound_wave_solver` / `sound_wave_reference`

Sound is represented as a pressure fluctuation, not as displacement of a
string or a collection of particles:

```text
p(x,t) = P0 cos(kx - omega t + phi)
k = omega / c_s, omega = 2 pi f
```

The probe reports pressure and its first two time derivatives. The scalar
field value unit is `Pa`; the field contract accepts physical value units and
continues to enforce finite values, shape, monotonic coordinates and resource
limits. This is an ideal travelling acoustic wave without attenuation,
reflection or room geometry.
