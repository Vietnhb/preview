package com.example.backend.physics.output;

import com.example.backend.physics.model.ScalarField;

import java.util.Objects;
import java.util.Optional;

public record ScalarFieldOutput(String key, ScalarField field) implements PhysicsOutput {
    public ScalarFieldOutput {
        ScalarOutput.requireKey(key);
        Objects.requireNonNull(field, "Scalar field is required");
    }

    @Override public Optional<String> unit() { return Optional.of(field.valueUnit()); }

    @Override public OutputKind kind() { return OutputKind.SCALAR_FIELD; }
}
