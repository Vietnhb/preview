package com.example.backend.physics.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

/**
 * Reads already-normalized physical quantities.
 *
 * Alias resolution belongs at the AI/specification boundary, where the
 * selected schema is available. Solvers deliberately receive canonical keys
 * only; this keeps numerical code deterministic as the catalog grows.
 */
public final class PhysicsValues {
    private static final double STANDARD_GRAVITY = 9.81;

    private PhysicsValues() {
    }

    private static Double find(JsonNode specification, Map<String, Double> overrides, String canonicalKey) {
        Double override = overrides == null ? null : overrides.get(canonicalKey);
        if (override != null)
            return override;
        JsonNode quantities = specification == null ? null : specification.get("quantities");
        if (quantities != null && quantities.isArray()) {
            for (JsonNode quantity : quantities) {
                if (canonicalKey.equals(quantity.path("name").asText()))
                    return numericValue(quantity);
            }
        }
        JsonNode direct = specification == null ? null : specification.get(canonicalKey);
        return direct != null && direct.isNumber() ? direct.asDouble() : null;
    }

    private static Double numericValue(JsonNode quantity) {
        if (quantity.path("normalizedValue").isNumber())
            return quantity.path("normalizedValue").asDouble();
        if (quantity.path("value").isNumber())
            return quantity.path("value").asDouble();
        return null;
    }

    public static double require(JsonNode specification, Map<String, Double> overrides, String canonicalKey) {
        Double value = find(specification, overrides, canonicalKey);
        if (value == null || !Double.isFinite(value)) {
            throw new IllegalArgumentException("Missing required physical quantity: " + canonicalKey);
        }
        return value;
    }

    public static double gravitationalAcceleration(JsonNode specification, Map<String, Double> overrides) {
        double gravity = optional(specification, overrides, STANDARD_GRAVITY, "gravitational_acceleration");
        if (gravity < 0)
            throw new IllegalArgumentException("Gravitational acceleration cannot be negative");
        return gravity;
    }

    public static double optional(JsonNode specification, Map<String, Double> overrides,
            double fallback, String canonicalKey) {
        Double value = find(specification, overrides, canonicalKey);
        if (value == null)
            return fallback;
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Optional physical quantity must be finite: " + canonicalKey);
        }
        return value;
    }

    public static String schema(JsonNode specification) {
        if (specification == null)
            throw new IllegalArgumentException("Specification is required");
        String schemaId = specification.path("schemaId").asText(specification.path("schema_id").asText());
        if (schemaId == null || schemaId.isBlank())
            throw new IllegalArgumentException("Specification schemaId is required");
        return canonicalName(schemaId);
    }

    public static String model(JsonNode specification) {
        if (specification == null || specification.path("model").asText().isBlank()) {
            throw new IllegalArgumentException("Specification model binding is required");
        }
        return canonicalName(specification.path("model").asText());
    }

    /**
     * Accepts historical dimension-suffixed identifiers without exposing them in
     * new contracts.
     */
    public static String canonicalName(String value) {
        if (value == null || value.isBlank())
            return value;
        return value.replaceFirst("(?i)[_-](?:1d|2d)$", "");
    }

    public static Map<String, ListBuilder> series(String... names) {
        Map<String, ListBuilder> result = new HashMap<>();
        for (String name : names) {
            result.put(name, new ListBuilder());
        }
        return result;
    }

    public static final class ListBuilder {
        private final java.util.List<Double> values = new java.util.ArrayList<>();

        public void add(double value) {
            values.add(value);
        }

        public java.util.List<Double> values() {
            return values;
        }
    }
}
