package com.example.backend.physics.output;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class PhysicsOutputValues {
    private PhysicsOutputValues() { }

    static Optional<String> validateUnit(Optional<String> unit) {
        if (unit == null) throw new IllegalArgumentException("Output unit optional is required");
        unit.ifPresent(value -> {
            if (value.isBlank()) throw new IllegalArgumentException("Output unit cannot be blank");
        });
        return unit;
    }

    static List<Double> copyFinite(List<Double> source, String label) {
        if (source == null) throw new IllegalArgumentException(label + " is required");
        List<Double> copy = new ArrayList<>(source.size());
        for (Double value : source) {
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException(label + " must contain only finite numbers");
            }
            copy.add(value);
        }
        return List.copyOf(copy);
    }

    static void validateTime(List<Double> times) {
        if (times.isEmpty()) throw new IllegalArgumentException("Time axis must not be empty");
        for (int index = 0; index < times.size(); index++) {
            if (times.get(index) < 0) throw new IllegalArgumentException("Time axis must be non-negative");
            if (index > 0 && times.get(index) <= times.get(index - 1)) {
                throw new IllegalArgumentException("Time axis must be strictly increasing");
            }
        }
    }
}
