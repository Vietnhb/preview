package com.example.backend.physics.module;

import com.example.backend.physics.runtime.SimulationTimeline;

import java.util.List;

/** Validated simulation horizon and sampling interval for typed physics modules. */
public record SimulationClock(double durationSeconds, double stepSeconds) {
    public SimulationClock {
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds must be finite and positive");
        }
        if (!Double.isFinite(stepSeconds) || stepSeconds <= 0) {
            throw new IllegalArgumentException("stepSeconds must be finite and positive");
        }
    }

    public List<Double> sampleTimes() {
        return SimulationTimeline.sample(durationSeconds, stepSeconds);
    }
}
