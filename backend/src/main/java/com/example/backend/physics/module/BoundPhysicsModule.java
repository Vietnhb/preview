package com.example.backend.physics.module;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.numerical.NumericalSolver;
import com.example.backend.physics.output.PhysicsOutputContract;
import com.example.backend.physics.output.PhysicsOutputFrame;
import com.example.backend.physics.output.PhysicsOutputFrameMapper;
import com.example.backend.physics.reference.ClosedFormReferenceSolver;

import java.util.Objects;

/** A module paired with parameters bound once from a canonical ingress bag. */
public final class BoundPhysicsModule {
    private final String moduleId;
    private final String numericalSolverId;
    private final String referenceSolverId;
    private final boolean nativeTypedOutput;
    private final BoundOperations operations;

    private <P> BoundPhysicsModule(PhysicsModule<P> module, P parameters) {
        PhysicsModule<P> typedModule = Objects.requireNonNull(module, "module");
        P typedParameters = Objects.requireNonNull(parameters, "parameters");
        NumericalSolver<P> numericalSolver = typedModule;
        ClosedFormReferenceSolver<P> closedFormReferenceSolver = typedModule;
        this.moduleId = typedModule.moduleId();
        this.numericalSolverId = typedModule.numericalSolverId();
        this.referenceSolverId = typedModule.referenceSolverId();
        this.nativeTypedOutput = typedModule.nativeTypedOutput();
        this.operations = new BoundOperations() {
            @Override
            public SolverOutput solve(SimulationClock clock) {
                return numericalSolver.solve(typedParameters, clock);
            }

            @Override
            public PhysicsOutputFrame solveTyped(PhysicsOutputContract contract, SimulationClock clock) {
                return numericalSolver.solveTyped(typedParameters, clock, contract);
            }

            @Override
            public AnalyticalPoint reference(double timeSeconds) {
                return closedFormReferenceSolver.referenceAt(typedParameters, timeSeconds);
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

    /**
     * Executes one bound module and exposes its result at the typed output
     * boundary. The legacy container is retained only inside this transitional
     * adapter so existing module implementations can migrate independently.
     */
    public SolvedOutput solve(PhysicsOutputContract contract, SimulationClock clock) {
        Objects.requireNonNull(contract, "output contract");
        SimulationClock requestedClock = Objects.requireNonNull(clock, "clock");
        SolverOutput legacy;
        PhysicsOutputFrame typed;
        if (nativeTypedOutput) {
            typed = operations.solveTyped(contract, requestedClock);
            legacy = PhysicsOutputFrameMapper.toSolverOutput(typed);
        } else {
            legacy = operations.solve(requestedClock);
            typed = PhysicsOutputFrameMapper.fromSolverOutput(legacy, contract);
        }
        return new SolvedOutput(legacy, typed);
    }

    public record SolvedOutput(SolverOutput legacy, PhysicsOutputFrame typed) {
        public SolvedOutput {
            Objects.requireNonNull(legacy, "legacy output");
            Objects.requireNonNull(typed, "typed output");
        }
    }

    public AnalyticalPoint closedFormReference(double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
            throw new IllegalArgumentException("timeSeconds must be finite and non-negative");
        }
        return operations.reference(timeSeconds);
    }

    /** @deprecated use {@link #closedFormReference(double)}. */
    @Deprecated
    public AnalyticalPoint reference(double timeSeconds) {
        return closedFormReference(timeSeconds);
    }

    private interface BoundOperations {
        SolverOutput solve(SimulationClock clock);

        PhysicsOutputFrame solveTyped(PhysicsOutputContract contract, SimulationClock clock);

        AnalyticalPoint reference(double timeSeconds);
    }
}
