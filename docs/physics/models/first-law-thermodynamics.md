# `first_law_thermodynamics`

The model uses the explicit convention that `W` is work done by the system:
`ΔU = Q − W`, `U_f = U_0 + ΔU`. Inputs and outputs are joules. The solver is
static in time because the lesson evaluates an energy balance, not a guessed
transient heat-transfer law or a pressure-volume path.
