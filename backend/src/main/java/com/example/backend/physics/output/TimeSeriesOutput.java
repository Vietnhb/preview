package com.example.backend.physics.output;

import java.util.List;
import java.util.Optional;

public record TimeSeriesOutput(String key, Optional<String> unit, List<Double> timeSeconds,
                               List<Double> values) implements PhysicsOutput {
    public TimeSeriesOutput {
        ScalarOutput.requireKey(key);
        unit = PhysicsOutputValues.validateUnit(unit);
        timeSeconds = PhysicsOutputValues.copyFinite(timeSeconds, "time axis");
        values = PhysicsOutputValues.copyFinite(values, "series values");
        if (timeSeconds.size() != values.size()) {
            throw new IllegalArgumentException("Time series axis and values must have equal length");
        }
        PhysicsOutputValues.validateTime(timeSeconds);
    }

    public TimeSeriesOutput(String key, String unit, List<Double> timeSeconds, List<Double> values) {
        this(key, Optional.of(unit), timeSeconds, values);
    }

    @Override public OutputKind kind() { return OutputKind.TIME_SERIES; }
}
