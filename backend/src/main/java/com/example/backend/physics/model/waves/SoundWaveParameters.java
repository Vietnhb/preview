package com.example.backend.physics.model.waves;

import com.example.backend.physics.model.ScalarField;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Canonical inputs for a harmonic acoustic pressure field. */
public record SoundWaveParameters(
        double pressureAmplitude,
        double frequency,
        double soundSpeed,
        double phase,
        double domainStart,
        double domainEnd,
        int spatialSamples,
        double probePosition) {

    public static final int DEFAULT_SPATIAL_SAMPLES = 161;

    public static SoundWaveParameters from(JsonNode specification, Map<String, Double> overrides) {
        double pressure = PhysicsValues.require(specification, overrides, "pressure_amplitude");
        double frequency = PhysicsValues.require(specification, overrides, "frequency");
        double soundSpeed = PhysicsValues.require(specification, overrides, "sound_speed");
        if (pressure < 0 || frequency <= 0 || soundSpeed <= 0) {
            throw new IllegalArgumentException("Sound pressure must be non-negative; frequency and speed must be positive");
        }
        double phase = PhysicsValues.optional(specification, overrides, 0, "phase");
        double wavelength = soundSpeed / frequency;
        double start = PhysicsValues.optional(specification, overrides, 0, "domain_start");
        double configuredEnd = PhysicsValues.optional(specification, overrides, Double.NaN, "domain_end");
        double configuredLength = PhysicsValues.optional(specification, overrides, Double.NaN, "domain_length");
        double end = Double.isFinite(configuredEnd) ? configuredEnd
                : start + (Double.isFinite(configuredLength) ? configuredLength : Math.max(2 * wavelength, 1));
        if (!Double.isFinite(start) || !Double.isFinite(end) || start < 0 || end <= start) {
            throw new IllegalArgumentException("Sound-wave domain must be finite, non-negative and increasing");
        }
        double samples = PhysicsValues.optional(specification, overrides, DEFAULT_SPATIAL_SAMPLES, "spatial_samples");
        if (!Double.isFinite(samples) || samples != Math.rint(samples) || samples < 2
                || samples > ScalarField.MAX_SPATIAL_SAMPLES) {
            throw new IllegalArgumentException("spatialSamples must be an integer within scalar-field limits");
        }
        int spatialSamples = (int) samples;
        double probe = PhysicsValues.optional(specification, overrides, start, "probe_position");
        if (!Double.isFinite(probe) || probe < start || probe > end) {
            throw new IllegalArgumentException("probePosition must lie within the sound-wave domain");
        }
        return new SoundWaveParameters(pressure, frequency, soundSpeed, phase, start, end, spatialSamples, probe);
    }

    public double angularFrequency() { return 2 * Math.PI * frequency; }
    public double waveNumber() { return angularFrequency() / soundSpeed; }
}
