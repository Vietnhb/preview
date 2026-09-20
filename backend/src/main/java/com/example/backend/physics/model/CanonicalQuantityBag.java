package com.example.backend.physics.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, lookup-efficient view of quantities after schema-boundary
 * canonicalization and unit normalization.
 *
 * <p>Physics code should consume this object (or a typed parameter object
 * created from it), never aliases or raw JSON. The legacy JsonNode bridge is
 * intentionally kept in {@link PhysicsValues} until all persisted payloads
 * have passed through the versioned adapter.</p>
 */
public final class CanonicalQuantityBag {
    private final Map<String, BigDecimal> values;
    private final Map<String, String> units;

    public CanonicalQuantityBag(Map<String, BigDecimal> values, Map<String, String> units) {
        if (values == null || units == null) throw new IllegalArgumentException("Quantity maps are required");
        Map<String, BigDecimal> copiedValues = new LinkedHashMap<>();
        Map<String, String> copiedUnits = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : values.entrySet()) {
            String key = normalizeKey(entry.getKey());
            BigDecimal value = entry.getValue();
            String unit = units.get(entry.getKey());
            if (key.isBlank() || value == null || unit == null || unit.isBlank()) {
                throw new IllegalArgumentException("Canonical quantity key, value and unit are required");
            }
            if (!Double.isFinite(value.doubleValue())) {
                throw new IllegalArgumentException("Canonical quantity must be finite: " + key);
            }
            if (copiedValues.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate canonical quantity: " + key);
            }
            copiedUnits.put(key, unit);
        }
        this.values = Map.copyOf(copiedValues);
        this.units = Map.copyOf(copiedUnits);
    }

    public static CanonicalQuantityBag empty() {
        return new CanonicalQuantityBag(Map.of(), Map.of());
    }

    public boolean contains(String key) {
        return values.containsKey(normalizeKey(key));
    }

    public double require(String key) {
        return requireDecimal(key).doubleValue();
    }

    public BigDecimal requireDecimal(String key) {
        String canonical = normalizeKey(key);
        BigDecimal value = values.get(canonical);
        if (value == null) throw new IllegalArgumentException("Missing required physical quantity: " + canonical);
        return value;
    }

    public double optional(String key, double fallback) {
        BigDecimal value = values.get(normalizeKey(key));
        return value == null ? fallback : value.doubleValue();
    }

    public String unit(String key) {
        return units.get(normalizeKey(key));
    }

    public Set<String> keys() {
        return values.keySet();
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim();
    }
}
