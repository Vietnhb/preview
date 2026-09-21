package com.example.backend.physics.compatibility.legacy.model.waves;

import com.example.backend.physics.model.ScalarField;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Canonical inputs for two coherent waves travelling in the positive x direction. */
public record WaveSuperpositionParameters(
        double amplitude1,
        double amplitude2,
        double frequency,
        double waveSpeed,
        double phase1,
        double phase2,
        double domainStart,
        double domainEnd,
        int spatialSamples,
        double probePosition) {

    public static final int DEFAULT_SPATIAL_SAMPLES = 161;

    public static WaveSuperpositionParameters from(JsonNode specification, Map<String, Double> overrides) {
        double amplitude1 = PhysicsValues.require(specification, overrides, "amplitude_1");
        double amplitude2 = PhysicsValues.require(specification, overrides, "amplitude_2");
        double frequency = PhysicsValues.require(specification, overrides, "frequency");
        double waveSpeed = PhysicsValues.require(specification, overrides, "wave_speed");
        if (amplitude1 < 0 || amplitude2 < 0 || frequency <= 0 || waveSpeed <= 0) {
            throw new IllegalArgumentException("Superposition amplitudes must be non-negative; frequency and speed must be positive");
        }
        double wavelength = waveSpeed / frequency;
        double phase1 = PhysicsValues.optional(specification, overrides, 0, "phase_1");
        double phase2 = PhysicsValues.optional(specification, overrides, 0, "phase_2");
        double domainStart = PhysicsValues.optional(specification, overrides, 0, "domain_start");
        double configuredEnd = PhysicsValues.optional(specification, overrides, Double.NaN, "domain_end");
        double configuredLength = PhysicsValues.optional(specification, overrides, Double.NaN, "domain_length");
        double domainEnd = Double.isFinite(configuredEnd) ? configuredEnd
                : domainStart + (Double.isFinite(configuredLength) ? configuredLength : Math.max(2 * wavelength, 1));
        if (!Double.isFinite(domainStart) || !Double.isFinite(domainEnd) || domainStart < 0 || domainEnd <= domainStart) {
            throw new IllegalArgumentException("Superposition domain must be finite, non-negative and increasing");
        }
        double samples = PhysicsValues.optional(specification, overrides, DEFAULT_SPATIAL_SAMPLES, "spatial_samples");
        if (!Double.isFinite(samples) || samples != Math.rint(samples) || samples < 2
                || samples > ScalarField.MAX_SPATIAL_SAMPLES) {
            throw new IllegalArgumentException("spatialSamples must be an integer within scalar-field limits");
        }
        int spatialSamples = (int) samples;
        double probe = PhysicsValues.optional(specification, overrides, domainStart, "probe_position");
        if (!Double.isFinite(probe) || probe < domainStart || probe > domainEnd) {
            throw new IllegalArgumentException("probePosition must lie within the superposition domain");
        }
        return new WaveSuperpositionParameters(amplitude1, amplitude2, frequency, waveSpeed, phase1, phase2,
                domainStart, domainEnd, spatialSamples, probe);
    }

    public double angularFrequency() { return 2 * Math.PI * frequency; }
    public double waveNumber() { return angularFrequency() / waveSpeed; }
}
