package com.example.backend.physics.compatibility.legacy.model.waves;

import com.example.backend.physics.model.ScalarField;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;
import java.util.Map;

/** Canonical inputs for a Gaussian pulse reflected by one ideal boundary. */
public record WaveReflectionParameters(
        double amplitude,
        double waveSpeed,
        double width,
        double initialPosition,
        double boundaryPosition,
        double domainStart,
        int spatialSamples,
        double probePosition,
        double reflectionCoefficient,
        String boundaryType) {

    public static final int DEFAULT_SPATIAL_SAMPLES = 161;

    public static WaveReflectionParameters from(JsonNode specification, Map<String, Double> overrides) {
        double amplitude = PhysicsValues.require(specification, overrides, "amplitude");
        double waveSpeed = PhysicsValues.require(specification, overrides, "wave_speed");
        double width = PhysicsValues.require(specification, overrides, "pulse_width");
        double initialPosition = PhysicsValues.require(specification, overrides, "initial_position");
        double boundary = PhysicsValues.require(specification, overrides, "boundary_position");
        if (amplitude < 0 || waveSpeed <= 0 || width <= 0) {
            throw new IllegalArgumentException("Reflection amplitude must be non-negative; speed and width must be positive");
        }

        double domainStart = PhysicsValues.optional(specification, overrides, 0, "domain_start");
        double samples = PhysicsValues.optional(specification, overrides, DEFAULT_SPATIAL_SAMPLES, "spatial_samples");
        if (!Double.isFinite(samples) || samples != Math.rint(samples) || samples < 2
                || samples > ScalarField.MAX_SPATIAL_SAMPLES) {
            throw new IllegalArgumentException("spatialSamples must be an integer within scalar-field limits");
        }
        int spatialSamples = (int) samples;
        double probe = PhysicsValues.optional(specification, overrides, initialPosition, "probe_position");
        if (!Double.isFinite(initialPosition) || !Double.isFinite(boundary) || !Double.isFinite(domainStart)
                || domainStart >= boundary || initialPosition < domainStart || initialPosition >= boundary
                || !Double.isFinite(probe) || probe < domainStart || probe > boundary) {
            throw new IllegalArgumentException("Reflection pulse position and boundary must be finite and ordered");
        }

        String type = boundaryType(specification);
        double coefficient = switch (type) {
            case "fixed" -> -1d;
            case "free" -> 1d;
            default -> throw new IllegalArgumentException("Unsupported reflection boundary type: " + type);
        };
        return new WaveReflectionParameters(amplitude, waveSpeed, width, initialPosition, boundary, domainStart,
                spatialSamples, probe, coefficient, type);
    }

    private static String boundaryType(JsonNode specification) {
        String direct = firstText(specification, "boundary_type", "boundaryType", "reflection_type", "reflectionType");
        if (direct == null && specification != null && specification.path("relations").isArray()) {
            for (JsonNode relation : specification.path("relations")) {
                String relationType = normalize(relation.path("type").asText());
                if (!"boundary_condition".equals(relationType) && !"reflection_boundary".equals(relationType)) continue;
                JsonNode value = relation.get("value");
                if (value != null && value.isTextual()) {
                    direct = value.asText();
                    break;
                }
            }
        }
        return normalize(direct == null || direct.isBlank() ? "fixed" : direct);
    }

    private static String firstText(JsonNode node, String... names) {
        if (node == null) return null;
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && value.isTextual() && !value.asText().isBlank()) return value.asText();
        }
        return null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
