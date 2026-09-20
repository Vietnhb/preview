# `ideal_gas_isothermal` — quasistatic isothermal process

- Model version: `1.0.0`
- Formula ID: `ideal-gas-isothermal.pv-constant.v1`
- Solver/reference: `ideal_gas_solver` / `ideal_gas_reference`

The model keeps the amount of gas and temperature constant while volume changes
at a configured rate:

```text
pV = nRT
V(t) = V0 + (dV/dt)t
p(t) = nRT / V(t)
W(t) = nRT ln(V(t)/V0)
```

The solver rejects a non-positive volume and reports pressure, volume,
temperature and work. `W` is work done by the gas and the logarithmic expression
assumes a quasistatic reversible path; a zero volume rate is the valid constant-
volume limit with `W=0`. It is an ideal-gas/isothermal model only; it does not
claim adiabatic, phase-change or real-gas behavior.
