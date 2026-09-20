# Backend physics topic packages

The backend physics implementation is partitioned by the canonical schema topic. Each topic owns its model parameters, numerical solver, and reference solver under the same topic slug:

```text
backend/src/main/java/com/example/backend/physics/
  model/<topic>/
  solver/<topic>/
  reference/<topic>/
```

Current topic packages are `kinematics`, `dynamics`, `circuits`, `electromagnetism`, `waves`, `thermal`, `optics`, `modern`, and `practical`.

Shared contracts remain at the package roots: `PhysicsValues`, `SolverOutput`, `AnalyticalPoint`, `ScalarField`, `WaveFieldGrid`, `PhysicsSolver`, `PhysicsSolverRegistry`, `ReferenceSolver`, and `ReferenceSolverRegistry`. Topic implementations import these contracts; they do not duplicate them.

The schema catalog remains the source of truth for the public topic identifier (`KINEMATICS`, `DYNAMICS`, `MODERN_PHYSICS`, etc.). Package slugs are internal Java names and are intentionally not inferred from user input. Registry binding continues to use the approved `solverId` and `referenceSolverId` in each schema.
