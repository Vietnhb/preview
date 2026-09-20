# `calorimetry_mixing`

Ideal isolated two-body calorimetry. With no phase change or heat loss and
constant specific heats,

`T_f = (m₁c₁T₁ + m₂c₂T₂)/(m₁c₁ + m₂c₂)` and `Q₁ + Q₂ = 0`.

Inputs use kg, J/(kg·K), and K. The solver emits the equilibrium state and
the heat received by each body; a reference solver recomputes the same
invariant independently. Since no heat-transfer coefficient or geometry is
provided, every time sample is the same equilibrium state; the time axis is a
presentation horizon, not a transient heat-flow prediction.
