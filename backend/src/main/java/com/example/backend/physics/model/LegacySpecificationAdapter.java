package com.example.backend.physics.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Compatibility boundary for persisted specifications written before the
 * canonical {@code quantities[]} contract. New production input must not use
 * this adapter; it is intentionally called only by the deprecated JsonNode
 * bridge in {@link PhysicsValues}.
 */
public final class LegacySpecificationAdapter {
    private static final AtomicLong INVOCATIONS = new AtomicLong();
    private LegacySpecificationAdapter() {
    }

    public static CanonicalQuantityBag adapt(JsonNode specification, Map<String, Double> overrides) {
        INVOCATIONS.incrementAndGet();
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        Map<String, String> units = new LinkedHashMap<>();
        JsonNode quantities = specification == null ? null : specification.get("quantities");
        if (quantities != null && quantities.isArray()) {
            for (JsonNode quantity : quantities) {
                String key = quantity.path("name").asText("").trim();
                if (key.isBlank()) continue;
                JsonNode numeric = quantity.path("normalizedValue").isNumber()
                        ? quantity.path("normalizedValue") : quantity.path("value");
                if (!numeric.isNumber()) continue;
                if (values.putIfAbsent(key, numeric.decimalValue()) != null) {
                    throw new IllegalArgumentException("Duplicate canonical quantity: " + key);
                }
                units.put(key, quantity.path("normalizedUnit").asText(
                        quantity.path("originalUnit").asText("1")));
            }
        }
        // Old records stored numeric fields at the document root. This path is
        // never used to resolve aliases: the key must already be canonical.
        if (specification != null && (quantities == null || !quantities.isArray() || quantities.isEmpty())) {
            specification.fieldNames().forEachRemaining(key -> {
                JsonNode value = specification.get(key);
                if (value != null && value.isNumber() && !values.containsKey(key)) {
                    values.put(key, value.decimalValue());
                    units.put(key, "1");
                }
            });
        }
        if (overrides != null) {
            overrides.forEach((key, value) -> {
                if (value == null || !Double.isFinite(value)) {
                    throw new IllegalArgumentException("Override must be finite: " + key);
                }
                values.put(key, BigDecimal.valueOf(value));
                units.putIfAbsent(key, "1");
            });
        }
        return new CanonicalQuantityBag(values, units);
    }

    /** Observable counter for migration telemetry; payload content is never logged. */
    public static long invocationCount() {
        return INVOCATIONS.get();
    }
}
