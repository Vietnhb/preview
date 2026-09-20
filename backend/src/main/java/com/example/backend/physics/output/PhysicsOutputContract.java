package com.example.backend.physics.output;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Versioned schema projection for typed output validation. */
public record PhysicsOutputContract(String schemaId, String schemaVersion, String modelId,
                                    Map<String, OutputDefinition> outputs, int maxSamples) {
    public PhysicsOutputContract {
        requireIdentity(schemaId, "schemaId");
        requireIdentity(schemaVersion, "schemaVersion");
        requireIdentity(modelId, "modelId");
        if (maxSamples < 1) throw new IllegalArgumentException("maxSamples must be positive");
        if (outputs == null || outputs.isEmpty()) throw new IllegalArgumentException("Output definitions are required");
        LinkedHashMap<String, OutputDefinition> copy = new LinkedHashMap<>();
        outputs.forEach((key, definition) -> {
            requireIdentity(key, "output key");
            copy.put(key, Objects.requireNonNull(definition, "Output definition is required"));
        });
        outputs = java.util.Collections.unmodifiableMap(copy);
    }

    private static void requireIdentity(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
    }

    public record OutputDefinition(PhysicsOutput.OutputKind kind, String unit, boolean required) {
        public OutputDefinition {
            Objects.requireNonNull(kind, "Output kind is required");
            if (unit == null || unit.isBlank()) throw new IllegalArgumentException("Output unit is required");
        }
    }
}
