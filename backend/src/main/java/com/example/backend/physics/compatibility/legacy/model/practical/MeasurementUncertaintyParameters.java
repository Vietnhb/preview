package com.example.backend.physics.compatibility.legacy.model.practical;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Basic absolute/relative uncertainty arithmetic for measured scalar quantities. */
public record MeasurementUncertaintyParameters(double measuredValue, double absoluteUncertainty) {
    public static MeasurementUncertaintyParameters from(JsonNode specification, Map<String, Double> overrides) {
        double value = PhysicsValues.require(specification, overrides, "measured_value");
        double uncertainty = PhysicsValues.require(specification, overrides, "absolute_uncertainty");
        if (!Double.isFinite(value) || !Double.isFinite(uncertainty) || uncertainty < 0)
            throw new IllegalArgumentException("Measured value must be finite and uncertainty non-negative");
        return new MeasurementUncertaintyParameters(value, uncertainty);
    }
    /** Relative uncertainty is undefined when the reported value is exactly zero. */
    public boolean relativeUncertaintyDefined() { return measuredValue != 0; }
    public double relativeUncertainty() { return measuredValue == 0 ? 0 : Math.abs(absoluteUncertainty / measuredValue); }
    public double lowerBound() { return measuredValue - absoluteUncertainty; }
    public double upperBound() { return measuredValue + absoluteUncertainty; }
}
