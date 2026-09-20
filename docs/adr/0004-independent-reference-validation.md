# Independent reference validation

Status: Accepted

Reference implementations must not call numerical solver methods or production formula kernels under test. The damped forced oscillator reference now evaluates its own closed-form branch equations. Validation compares finite outputs with per-output absolute/relative tolerances (and exact/discrete modes), reports both error measures, and rejects missing or empty reference output.
