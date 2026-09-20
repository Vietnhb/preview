package com.example.backend.physics.output;

import java.util.List;

/** One numerical run's shared timeline and immutable typed outputs. */
public record PhysicsOutputFrame(List<Double> timeSeconds, List<PhysicsOutput> outputs) {
    public PhysicsOutputFrame {
        timeSeconds = PhysicsOutputValues.copyFinite(timeSeconds, "run time axis");
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
    }
}
