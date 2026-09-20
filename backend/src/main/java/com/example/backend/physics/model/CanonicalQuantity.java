package com.example.backend.physics.model;

import java.math.BigDecimal;

/**
 * A schema-bound quantity after canonical-name and unit normalization.
 * Presentation aliases and symbols are deliberately not part of this type.
 */
public record CanonicalQuantity(String key, BigDecimal value, String unit, String sourceText) {
    public CanonicalQuantity {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Canonical quantity key is required");
        if (value == null || !Double.isFinite(value.doubleValue())) {
            throw new IllegalArgumentException("Canonical quantity value must be finite: " + key);
        }
        if (unit == null || unit.isBlank()) throw new IllegalArgumentException("Canonical quantity unit is required: " + key);
    }
}
