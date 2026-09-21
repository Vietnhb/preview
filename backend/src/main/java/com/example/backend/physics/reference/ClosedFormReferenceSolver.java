package com.example.backend.physics.reference;

import com.example.backend.physics.model.AnalyticalPoint;

/** Independent analytical/closed-form path used as the validation reference. */
public interface ClosedFormReferenceSolver<P> {
    AnalyticalPoint referenceAt(P parameters, double timeSeconds);
}
