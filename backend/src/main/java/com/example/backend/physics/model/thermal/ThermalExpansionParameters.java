package com.example.backend.physics.model.thermal;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Linear expansion of a homogeneous solid for a small temperature change. */
public record ThermalExpansionParameters(double initialLength, double coefficient,
                                         double initialTemperature, double finalTemperature) {
    public static ThermalExpansionParameters from(JsonNode specification, Map<String, Double> overrides) {
        double length = PhysicsValues.require(specification, overrides, "initial_length");
        double alpha = PhysicsValues.require(specification, overrides, "linear_expansion_coefficient");
        double t0 = PhysicsValues.require(specification, overrides, "initial_temperature");
        double t1 = PhysicsValues.require(specification, overrides, "final_temperature");
        if (!(length > 0) || alpha < 0 || !(t0 > 0) || !(t1 > 0)
                || !Double.isFinite(alpha) || !Double.isFinite(t0) || !Double.isFinite(t1))
            throw new IllegalArgumentException("Length positive, coefficient non-negative and absolute temperatures positive");
        return new ThermalExpansionParameters(length, alpha, t0, t1);
    }
    public double deltaTemperature() { return finalTemperature - initialTemperature; }
    public double extension() { return initialLength * coefficient * deltaTemperature(); }
    public double finalLength() { return initialLength + extension(); }
}
