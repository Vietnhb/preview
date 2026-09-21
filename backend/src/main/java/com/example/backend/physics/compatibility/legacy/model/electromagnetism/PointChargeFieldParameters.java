package com.example.backend.physics.compatibility.legacy.model.electromagnetism;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Electric field and potential of an isolated point charge. */
public record PointChargeFieldParameters(double charge, double distance) {
    public static final double COULOMB_CONSTANT = 8.9875517923e9;
    public static PointChargeFieldParameters from(JsonNode specification, Map<String, Double> overrides) {
        double q = PhysicsValues.require(specification, overrides, "charge");
        double r = PhysicsValues.require(specification, overrides, "distance");
        if (!Double.isFinite(q) || !Double.isFinite(r) || r <= 0)
            throw new IllegalArgumentException("Charge must be finite; distance positive");
        return new PointChargeFieldParameters(q, r);
    }
    public double electricFieldMagnitude() { return COULOMB_CONSTANT * Math.abs(charge) / (distance * distance); }
    public double electricPotential() { return COULOMB_CONSTANT * charge / distance; }
}
