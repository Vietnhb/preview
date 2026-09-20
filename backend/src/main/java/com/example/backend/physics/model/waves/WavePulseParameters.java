package com.example.backend.physics.model.waves;

import com.example.backend.physics.model.ScalarField;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

public record WavePulseParameters(double amplitude, double waveSpeed, double width,
                                  double initialPosition, double domainStart, double domainEnd,
                                  int spatialSamples, double probePosition) {
    public static final int DEFAULT_SPATIAL_SAMPLES = 161;

    public static WavePulseParameters from(JsonNode specification, Map<String, Double> overrides) {
        double amplitude = PhysicsValues.require(specification, overrides, "amplitude");
        double speed = PhysicsValues.require(specification, overrides, "wave_speed");
        double width = PhysicsValues.require(specification, overrides, "pulse_width");
        if (amplitude < 0 || speed <= 0 || width <= 0) {
            throw new IllegalArgumentException("Pulse amplitude must be non-negative; speed and width must be positive");
        }
        double initialPosition = PhysicsValues.optional(specification, overrides, 0,
                "initial_position");
        double domainStart = PhysicsValues.optional(specification, overrides,
                Math.min(0, initialPosition - 4 * width), "domain_start");
        double configuredEnd = PhysicsValues.optional(specification, overrides, Double.NaN,
                "domain_end");
        double configuredLength = PhysicsValues.optional(specification, overrides, Double.NaN,
                "domain_length");
        double domainEnd = Double.isFinite(configuredEnd) ? configuredEnd
                : domainStart + (Double.isFinite(configuredLength) ? configuredLength : Math.max(8 * width, 1));
        if (!Double.isFinite(initialPosition) || !Double.isFinite(domainStart) || !Double.isFinite(domainEnd)
                || domainEnd <= domainStart || initialPosition < domainStart || initialPosition > domainEnd) {
            throw new IllegalArgumentException("Pulse position and domain must be finite and ordered");
        }
        double samples = PhysicsValues.optional(specification, overrides, DEFAULT_SPATIAL_SAMPLES,
                "spatial_samples");
        if (!Double.isFinite(samples) || samples != Math.rint(samples) || samples < 2 || samples > ScalarField.MAX_SPATIAL_SAMPLES) {
            throw new IllegalArgumentException("spatialSamples must be an integer within scalar-field limits");
        }
        int spatialSamples = (int) samples;
        double probe = PhysicsValues.optional(specification, overrides, initialPosition,
                "probe_position");
        if (!Double.isFinite(probe) || probe < domainStart || probe > domainEnd) {
            throw new IllegalArgumentException("probePosition must lie within the pulse domain");
        }
        return new WavePulseParameters(amplitude, speed, width, initialPosition, domainStart, domainEnd,
                spatialSamples, probe);
    }
}
