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
    private PhysicsValues() {
    }

    /** @deprecated Use a schema-bound CanonicalQuantityBag in new code. */
    @Deprecated
    public static double require(JsonNode specification, Map<String, Double> overrides, String canonicalKey) {
        return LegacySpecificationAdapter.adapt(specification, overrides).require(canonicalKey);
    }

    /**
     * Compiles the already-normalized specification quantities once for a
     * parameter binder. New model code should prefer this boundary over doing
     * repeated JsonNode scans for every field.
     */
    public static CanonicalQuantityBag bag(JsonNode specification, Map<String, Double> overrides) {
        Map<String, java.math.BigDecimal> values = new java.util.LinkedHashMap<>();
        Map<String, String> units = new java.util.LinkedHashMap<>();
        JsonNode quantities = specification == null ? null : specification.get("quantities");
        // Direct solver calls from pre-canonical replay fixtures may still lack
        // quantities[]. Keep that migration path explicit and isolated in the
        // adapter; production specifications always take the branch below.
        if (quantities == null || !quantities.isArray() || quantities.isEmpty()) {
            return LegacySpecificationAdapter.adapt(specification, overrides);
        }
        if (quantities != null && quantities.isArray()) {
            for (JsonNode quantity : quantities) {
                String key = quantity.path("name").asText("").trim();
                if (key.isBlank()) continue;
                JsonNode numeric = quantity.path("normalizedValue").isNumber()
                        ? quantity.path("normalizedValue") : quantity.path("value");
                if (!numeric.isNumber()) continue;
                String unit = quantity.path("normalizedUnit").asText(quantity.path("originalUnit").asText("1"));
                if (values.putIfAbsent(key, numeric.decimalValue()) != null) {
                    throw new IllegalArgumentException("Duplicate canonical quantity: " + key);
                }
                units.put(key, unit);
            }
        }
        if (overrides != null) {
            overrides.forEach((key, value) -> {
                if (value == null || !Double.isFinite(value)) {
                    throw new IllegalArgumentException("Override must be finite: " + key);
                }
                values.put(key, java.math.BigDecimal.valueOf(value));
                units.putIfAbsent(key, "1");
            });
        }
        return new CanonicalQuantityBag(values, units);
    }

    public static double require(CanonicalQuantityBag quantities, String canonicalKey) {
        if (quantities == null) throw new IllegalArgumentException("Canonical quantities are required");
        return quantities.require(canonicalKey);
    }

    public static double optional(CanonicalQuantityBag quantities, double fallback, String canonicalKey) {
        if (quantities == null) return fallback;
        return quantities.optional(canonicalKey, fallback);
    }

    public static double gravitationalAcceleration(JsonNode specification, Map<String, Double> overrides) {
        double gravity = optional(specification, overrides, PhysicalConstants.STANDARD_GRAVITY,
                "gravitational_acceleration");
        if (gravity < 0)
            throw new IllegalArgumentException("Gravitational acceleration cannot be negative");
        return gravity;
    }

    public static double optional(JsonNode specification, Map<String, Double> overrides,
            double fallback, String canonicalKey) {
        CanonicalQuantityBag bag = LegacySpecificationAdapter.adapt(specification, overrides);
        return bag.optional(canonicalKey, fallback);
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
