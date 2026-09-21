package com.example.backend.physics.compatibility.legacy.model.waves;

import com.example.backend.physics.model.ScalarField;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public record StandingWaveParameters(double amplitude, double frequency, double waveSpeed,
        double phase, double domainStart, double domainEnd,
        int spatialSamples, double probePosition) {
    public static final int DEFAULT_SPATIAL_SAMPLES = 161;

    public static StandingWaveParameters from(JsonNode specification, Map<String, Double> overrides) {
        double amplitude = PhysicsValues.require(specification, overrides, "amplitude");
        double frequency = PhysicsValues.require(specification, overrides, "frequency");
        double speed = PhysicsValues.require(specification, overrides, "wave_speed");
        double length = PhysicsValues.require(specification, overrides, "string_length");
        if (amplitude < 0 || frequency <= 0 || speed <= 0 || length <= 0) {
            throw new IllegalArgumentException(
                    "Standing-wave amplitude must be non-negative; frequency, speed and length must be positive");
        }
        double phase = PhysicsValues.optional(specification, overrides, 0, "phase");
        double start = PhysicsValues.optional(specification, overrides, 0, "domain_start");
        double end = start + length;
        double samples = PhysicsValues.optional(specification, overrides, DEFAULT_SPATIAL_SAMPLES,
                "spatial_samples");
        if (!Double.isFinite(samples) || samples != Math.rint(samples) || samples < 2
                || samples > ScalarField.MAX_SPATIAL_SAMPLES) {
            throw new IllegalArgumentException("spatialSamples must be an integer within scalar-field limits");
        }
        double probe = PhysicsValues.optional(specification, overrides, start + length / 2,
                "probe_position");
        if (!Double.isFinite(start) || !Double.isFinite(probe) || probe < start || probe > end) {
            throw new IllegalArgumentException("Standing-wave probe must lie within the domain");
        }
        return new StandingWaveParameters(amplitude, frequency, speed, phase, start, end,
                (int) samples, probe);
    }

    public double angularFrequency() {
        return 2 * Math.PI * frequency;
    }

    public double waveNumber() {
        return 2 * Math.PI * frequency / waveSpeed;
    }
}
