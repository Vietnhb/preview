package com.example.backend.physics.module;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.numerical.NumericalSolver;
import com.example.backend.physics.reference.ClosedFormReferenceSolver;

/** Typed physics binding composed of a numerical solver and a closed-form reference solver. */
public interface PhysicsModule<P> extends NumericalSolver<P>, ClosedFormReferenceSolver<P> {
    String moduleId();

    String numericalSolverId();

    String referenceSolverId();

    P bind(CanonicalQuantityBag quantities);

}
