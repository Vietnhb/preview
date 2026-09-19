package com.example.backend.physics;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class PhysicsValues {
    private static final double STANDARD_GRAVITY = 9.81;

    private PhysicsValues() {
    }

    private static Double find(JsonNode specification, Map<String, Double> overrides, String... names) {
        Double override = findOverride(overrides, names);
        if (override != null) return override;
        Double quantity = findQuantity(specification, names);
        if (quantity != null) return quantity;
        return findDirect(specification, names);
    }

    private static Double findOverride(Map<String, Double> overrides, String... names) {
        if (overrides == null) return null;
        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (overrides.containsKey(lower)) {
                return overrides.get(lower);
            }
            if (overrides.containsKey(name)) {
                return overrides.get(name);
            }
        }
        return null;
    }

    private static Double findQuantity(JsonNode specification, String... names) {
        JsonNode quantities = specification == null ? null : specification.get("quantities");
        if (quantities != null && quantities.isArray()) {
            for (JsonNode quantity : quantities) {
                String actual = quantity.path("name").asText("").toLowerCase(Locale.ROOT);
                String symbol = quantity.path("symbol").asText("").toLowerCase(Locale.ROOT);
                for (String name : names) {
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (matches(actual, lower) || matches(symbol, lower)) {
                        return numericValue(quantity);
                    }
                }
            }
        }
        return null;
    }

    private static Double numericValue(JsonNode quantity) {
        if (quantity.path("normalizedValue").isNumber()) return quantity.path("normalizedValue").asDouble();
        if (quantity.path("value").isNumber()) return quantity.path("value").asDouble();
        return null;
    }

    private static Double findDirect(JsonNode specification, String... names) {
        if (specification == null) return null;
        for (String name : names) {
            JsonNode direct = specification.get(name);
            if (direct != null && direct.isNumber()) {
                return direct.asDouble();
            }
        }
        return null;
    }

    static double require(JsonNode specification, Map<String, Double> overrides, String... names) {
        Double value = find(specification, overrides, names);
        if (value == null || !Double.isFinite(value)) {
            throw new IllegalArgumentException("Missing required physical quantity: " + String.join("/", names));
        }
        return value;
    }

    static double gravitationalAcceleration(JsonNode specification, Map<String, Double> overrides) {
        double gravity = optional(specification, overrides, STANDARD_GRAVITY,
                "gravitational_acceleration", "gravity", "g");
        if (gravity < 0) throw new IllegalArgumentException("Gravitational acceleration cannot be negative");
        return gravity;
    }

    private static double optional(JsonNode specification, Map<String, Double> overrides,
                                   double fallback, String... names) {
        Double value = find(specification, overrides, names);
        if (value == null) return fallback;
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Optional physical quantity must be finite: " + String.join("/", names));
        }
        return value;
    }

    private static boolean matches(String candidate, String target) {
        if (candidate.isBlank() || target.isBlank()) return false;
        if (candidate.equals(target)) return true;
        return matchesPosition(candidate, target)
                || matchesVelocity(candidate, target)
                || matchesCollision(candidate, target)
                || matchesHeight(candidate, target)
                || matchesAngle(candidate, target);
    }

    private static boolean matchesPosition(String candidate, String target) {
        return (candidate.equals("distance") || candidate.equals("s"))
                && (target.equals("initial_position") || target.equals("x0") || target.equals("position"));
    }

    private static boolean matchesVelocity(String candidate, String target) {
        return (candidate.equals("speed") || candidate.equals("v"))
                && (target.equals("initial_velocity") || target.equals("v0") || target.equals("velocity"));
    }

    private static boolean matchesCollision(String candidate, String target) {
        return (candidate.equals("v1") && target.equals("velocity_1"))
                || (candidate.equals("v2") && target.equals("velocity_2"))
                || (candidate.equals("m1") && target.equals("mass_1"))
                || (candidate.equals("m2") && target.equals("mass_2"));
    }

    private static boolean matchesHeight(String candidate, String target) {
        return candidate.equals("h")
                && (target.equals("initial_height") || target.equals("y0") || target.equals("height"));
    }

    private static boolean matchesAngle(String candidate, String target) {
        return (candidate.equals("angle") || candidate.equals("theta")) && target.equals("launch_angle");
    }

    static String schema(JsonNode specification) {
        if (specification == null) throw new IllegalArgumentException("Specification is required");
        String schemaId = specification.path("schemaId").asText(specification.path("schema_id").asText());
        if (schemaId == null || schemaId.isBlank()) throw new IllegalArgumentException("Specification schemaId is required");
        return schemaId;
    }

    static String model(JsonNode specification) {
        if (specification == null || specification.path("model").asText().isBlank()) {
            throw new IllegalArgumentException("Specification model binding is required");
        }
        return specification.path("model").asText();
    }

    static Map<String, ListBuilder> series(String... names) {
        Map<String, ListBuilder> result = new HashMap<>();
        for (String name : names) {
            result.put(name, new ListBuilder());
        }
        return result;
    }

    static final class ListBuilder {
        private final java.util.List<Double> values = new java.util.ArrayList<>();

        void add(double value) {
            values.add(value);
        }

        java.util.List<Double> values() {
            return values;
        }
    }
}
