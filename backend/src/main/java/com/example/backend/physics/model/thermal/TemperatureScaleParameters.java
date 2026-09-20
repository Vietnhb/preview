package com.example.backend.physics.model.thermal;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Canonical input for temperature-scale conversion. */
public record TemperatureScaleParameters(double celsius) {
    public static TemperatureScaleParameters from(JsonNode specification, Map<String, Double> overrides) {
        double value = PhysicsValues.require(specification, overrides, "temperature_celsius");
        if (!Double.isFinite(value) || value < -273.15) {
            throw new IllegalArgumentException("Temperature must be finite and not below absolute zero");
        }
        return new TemperatureScaleParameters(value);
    }
    public double kelvin() { return celsius + 273.15; }
    public double fahrenheit() { return celsius * 9.0 / 5.0 + 32.0; }
}
