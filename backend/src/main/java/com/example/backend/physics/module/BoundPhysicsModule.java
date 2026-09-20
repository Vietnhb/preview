package com.example.backend.physics.module;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.SolverOutput;

import java.util.Objects;

/** A module paired with parameters bound once from a canonical ingress bag. */
public final class BoundPhysicsModule {
    private final String moduleId;
    private final String numericalSolverId;
    private final String referenceSolverId;
    private final BoundOperations operations;

    private <P> BoundPhysicsModule(PhysicsModule<P> module, P parameters) {
        PhysicsModule<P> typedModule = Objects.requireNonNull(module, "module");
        P typedParameters = Objects.requireNonNull(parameters, "parameters");
        this.moduleId = typedModule.moduleId();
        this.numericalSolverId = typedModule.numericalSolverId();
        this.referenceSolverId = typedModule.referenceSolverId();
        this.operations = new BoundOperations() {
            @Override
            public SolverOutput solve(SimulationClock clock) {
                return typedModule.solve(typedParameters, clock);
            }

            @Override
            public AnalyticalPoint reference(double timeSeconds) {
                return typedModule.referenceAt(typedParameters, timeSeconds);
            }
        };
    }

    static <P> BoundPhysicsModule bind(PhysicsModule<P> module, P parameters) {
        return new BoundPhysicsModule(module, parameters);
    }

    public String moduleId() {
        return moduleId;
    }

    public String numericalSolverId() {
        return numericalSolverId;
    }

    public String referenceSolverId() {
        return referenceSolverId;
    }

    public SolverOutput solve(SimulationClock clock) {
        return operations.solve(Objects.requireNonNull(clock, "clock"));
    }

    public AnalyticalPoint reference(double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
            throw new IllegalArgumentException("timeSeconds must be finite and non-negative");
        }
        return operations.reference(timeSeconds);
    }

    private interface BoundOperations {
        SolverOutput solve(SimulationClock clock);

        AnalyticalPoint reference(double timeSeconds);
    }
}
