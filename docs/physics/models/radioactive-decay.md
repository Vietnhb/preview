# `radioactive_decay` — exponential decay expectation

- Model version: `1.0.0`
- Formula ID: `radioactive-decay.exponential.v1`
- Solver/reference: `radioactive_decay_solver` / `radioactive_decay_reference`

The expected remaining nuclei and activity are:

```text
N(t) = N0 exp(-lambda t)
A(t) = lambda N(t)
t_half = ln(2) / lambda
```

This is a deterministic expectation-value model for teaching half-life and
activity. `N0=0` and `lambda=0` are valid stable/empty limits; the half-life
expression is only defined for `lambda>0`. It does not synthesize random
detector counts or replace a radiation safety activity.
