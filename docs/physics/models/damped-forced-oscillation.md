# Damped forced oscillation

The schema uses `damping_coefficient` in kg/s for

`m x'' + c x' + k x = F0 cos(ωt)`.

The rate used by the solution is `γ = c/(2m)`, while `ω0 = sqrt(k/m)`. Therefore

`ωd = sqrt(ω0² - γ²)`,
`A = (F0/m) / sqrt((ω0²-ω²)² + (2γω)²)`, and
`φ = atan2(2γω, ω0²-ω²)`.

The numerical model distinguishes under-damped (`γ < ω0`), critical (`γ = ω0`), and over-damped (`γ > ω0`) regimes. An undamped non-zero resonant steady-state is rejected as unbounded; zero forcing remains finite.
