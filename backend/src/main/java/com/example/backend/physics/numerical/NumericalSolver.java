package com.example.backend.physics.numerical;

import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.SimulationClock;
import com.example.backend.physics.output.PhysicsOutputContract;
import com.example.backend.physics.output.PhysicsOutputFrame;
import com.example.backend.physics.output.PhysicsOutputFrameMapper;

import java.util.Objects;

/** Numerical path that produces the time-stepped simulation output. */
public interface NumericalSolver<P> {
    SolverOutput solve(P parameters, SimulationClock clock);

    default PhysicsOutputFrame solveTyped(P parameters, SimulationClock clock,
                                           PhysicsOutputContract contract) {
        Objects.requireNonNull(contract, "output contract");
        return PhysicsOutputFrameMapper.fromSolverOutput(solve(parameters, clock), contract);
    }

    default boolean nativeTypedOutput() {
        return false;
    }
}
