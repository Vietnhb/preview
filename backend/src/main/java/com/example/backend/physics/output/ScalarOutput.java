package com.example.backend.physics.output;

import java.util.Optional;

public record ScalarOutput(String key, Optional<String> unit, double value) implements PhysicsOutput {
    public ScalarOutput {
        requireKey(key);
        unit = PhysicsOutputValues.validateUnit(unit);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Scalar output value must be finite");
    }

    public ScalarOutput(String key, String unit, double value) {
        this(key, Optional.of(unit), value);
    }

    @Override public OutputKind kind() { return OutputKind.SCALAR; }

    static void requireKey(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Output key is required");
    }
}
