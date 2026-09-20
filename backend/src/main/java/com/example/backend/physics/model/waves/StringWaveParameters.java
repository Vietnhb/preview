package com.example.backend.physics.model.waves;

import com.example.backend.physics.model.ScalarField;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.Map;

/**
 * Parsed input for a transverse wave travelling in the positive x direction.
 * The parser deliberately accepts a source mode expressed as a relation because
 * a persisted {@code Specification} serializes relations but not arbitrary
 * raw fields.
 */
public record StringWaveParameters(
        double amplitude,
        double frequency,
        double waveSpeed,
        double phase,
        double domainStart,
        double domainEnd,
        int spatialSamples,
        double probePosition,
        String sourceBehavior,
        double sourceRampSeconds) {

    public static final String PERIODIC = "periodic";
    public static final String SOURCE_STARTED = "source_started";
    public static final int DEFAULT_SPATIAL_SAMPLES = 81;

    public static StringWaveParameters from(JsonNode specification, Map<String, Double> overrides) {
        double amplitude = PhysicsValues.require(specification, overrides, "amplitude");
        double frequency = PhysicsValues.require(specification, overrides, "frequency");
        double waveSpeed = PhysicsValues.require(specification, overrides, "wave_speed");
        double phase = PhysicsValues.optional(specification, overrides, 0, "phase");
        if (amplitude < 0 || frequency <= 0 || waveSpeed <= 0) {
            throw new IllegalArgumentException(
                    "Wave amplitude must be non-negative and frequency/speed must be positive");
        }

        double wavelength = waveSpeed / frequency;
        double domainStart = PhysicsValues.optional(specification, overrides, 0,
                "domain_start");
        double configuredEnd = PhysicsValues.optional(specification, overrides, Double.NaN,
                "domain_end");
        double configuredLength = PhysicsValues.optional(specification, overrides, Double.NaN,
                "domain_length");
        double domainEnd = Double.isFinite(configuredEnd) ? configuredEnd
                : domainStart + (Double.isFinite(configuredLength) ? configuredLength : Math.max(1, 2 * wavelength));
        if (!Double.isFinite(domainStart) || !Double.isFinite(domainEnd) || domainStart < 0
                || domainEnd <= domainStart) {
            throw new IllegalArgumentException(
                    "String-wave domain must be finite, non-negative and increasing from the source");
        }

        double samples = PhysicsValues.optional(specification, overrides, DEFAULT_SPATIAL_SAMPLES,
                "spatial_samples");
        if (!Double.isFinite(samples) || samples != Math.rint(samples) || samples < 2 || samples > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("spatialSamples must be a finite integer greater than one");
        }
        int spatialSamples = (int) samples;
        if (spatialSamples > ScalarField.MAX_SPATIAL_SAMPLES) {
            throw new IllegalArgumentException("spatialSamples exceeds scalar-field resource limits");
        }

        double probePosition = PhysicsValues.optional(specification, overrides, domainStart,
                "probe_position");
        if (!Double.isFinite(probePosition) || probePosition < domainStart || probePosition > domainEnd) {
            throw new IllegalArgumentException("probePosition must lie within the string-wave domain");
        }

        String sourceBehavior = sourceBehavior(specification);
        double sourceRamp = sourceBehavior.equals(SOURCE_STARTED)
                ? PhysicsValues.optional(specification, overrides, 1d / (4d * frequency),
                        "source_ramp_seconds")
                : 0;
        if (!Double.isFinite(sourceRamp) || (sourceBehavior.equals(SOURCE_STARTED) && sourceRamp <= 0)) {
            throw new IllegalArgumentException("sourceRampSeconds must be positive for source_started waves");
        }
        return new StringWaveParameters(amplitude, frequency, waveSpeed, phase, domainStart, domainEnd,
                spatialSamples, probePosition, sourceBehavior, sourceRamp);
    }

    public double wavelength() {
        return waveSpeed / frequency;
    }

    public double waveNumber() {
        return 2 * Math.PI / wavelength();
    }

    public double angularFrequency() {
        return 2 * Math.PI * frequency;
    }

    private static String sourceBehavior(JsonNode specification) {
        String direct = firstText(specification, "sourceBehavior", "source_behavior", "sourceMode", "source_mode",
                "waveMode", "wave_mode");
        if (direct == null && specification != null && specification.path("relations").isArray()) {
            for (JsonNode relation : specification.path("relations")) {
                String type = normalize(relation.path("type").asText());
                if (!"wave_mode".equals(type) && !"source_mode".equals(type))
                    continue;
                JsonNode value = relation.get("value");
                if (value != null && value.isTextual()) {
                    direct = value.asText();
                    break;
                }
                direct = firstText(relation, "mode", "sourceBehavior", "source_behavior");
                if (direct != null)
                    break;
            }
        }
        if (direct == null || direct.isBlank())
            return PERIODIC;
        String normalized = normalize(direct);
        if (PERIODIC.equals(normalized) || SOURCE_STARTED.equals(normalized))
            return normalized;
        throw new IllegalArgumentException("Unsupported string-wave source behavior: " + direct);
    }

    private static String firstText(JsonNode node, String... names) {
        if (node == null)
            return null;
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isTextual() && !value.asText().isBlank())
                return value.asText();
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
