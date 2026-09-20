package com.example.backend.physics.module;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;

/** Typed owner of one model's parameter binding, numerical solver and reference oracle. */
public interface PhysicsModule<P> {
    String moduleId();

    String numericalSolverId();

    String referenceSolverId();

    P bind(CanonicalQuantityBag quantities);

    SolverOutput solve(P parameters, SimulationClock clock);

    AnalyticalPoint referenceAt(P parameters, double timeSeconds);
}
