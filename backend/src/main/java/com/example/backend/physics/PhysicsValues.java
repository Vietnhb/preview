package com.example.backend.physics;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

final class PhysicsValues {
    private PhysicsValues() {
    }

    private static Double find(JsonNode specification, Map<String, Double> overrides, String... names) {
        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (overrides != null && overrides.containsKey(lower)) {
                return overrides.get(lower);
            }
            if (overrides != null && overrides.containsKey(name)) {
                return overrides.get(name);
            }
        }
        JsonNode quantities = specification == null ? null : specification.get("quantities");
        if (quantities != null && quantities.isArray()) {
            for (JsonNode quantity : quantities) {
                String actual = quantity.path("name").asText("").toLowerCase(Locale.ROOT);
                String symbol = quantity.path("symbol").asText("").toLowerCase(Locale.ROOT);
                for (String name : names) {
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (matches(actual, lower) || matches(symbol, lower)) {
                        return quantity.path("normalizedValue").isNumber()
                                ? quantity.path("normalizedValue").asDouble()
                                : quantity.path("value").isNumber() ? quantity.path("value").asDouble() : null;
                    }
                }
            }
        }
        for (String name : names) {
            JsonNode direct = specification == null ? null : specification.get(name);
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

    private static boolean matches(String candidate, String target) {
        if (candidate.isBlank() || target.isBlank()) return false;
        if (candidate.equals(target)) return true;
        if ((candidate.equals("distance") || candidate.equals("s"))
                && (target.equals("initial_position") || target.equals("x0") || target.equals("position"))) return true;
        if ((candidate.equals("speed") || candidate.equals("v"))
                && (target.equals("initial_velocity") || target.equals("v0") || target.equals("velocity"))) return true;
        if (candidate.equals("v1") && target.equals("velocity_1")) return true;
        if (candidate.equals("v2") && target.equals("velocity_2")) return true;
        if (candidate.equals("m1") && target.equals("mass_1")) return true;
        if (candidate.equals("m2") && target.equals("mass_2")) return true;
        if (candidate.equals("h") && (target.equals("initial_height") || target.equals("y0") || target.equals("height"))) return true;
        if ((candidate.equals("angle") || candidate.equals("theta")) && target.equals("launch_angle")) return true;
        return false;
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
