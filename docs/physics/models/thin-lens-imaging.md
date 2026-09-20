# `thin_lens_imaging` — paraxial thin lens

- Model version: `1.0.0`
- Formula ID: `thin-lens-imaging.gaussian.v1`
- Solver/reference: `thin_lens_solver` / `thin_lens_reference`

For a thin lens in the paraxial approximation:

```text
1/f = 1/d_o + 1/d_i
M = h_i/h_o = -d_i/d_o
```

The model rejects an object exactly at the focal point, where the finite image
distance assumption fails. It does not claim thick-lens aberration or a full
ray-tracing geometry.
