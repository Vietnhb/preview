package com.example.backend.physics.output;

import java.util.Optional;

/** Immutable, typed output value emitted by a physics module. */
public sealed interface PhysicsOutput permits ScalarOutput, TimeSeriesOutput,
        VectorSeriesOutput, ScalarFieldOutput {
    String key();

    /**
     * Empty only when an older solver has no declared unit yet. New module
     * contracts should always supply a unit.
     */
    Optional<String> unit();

    OutputKind kind();

    enum OutputKind { SCALAR, TIME_SERIES, VECTOR_SERIES, SCALAR_FIELD }
}
