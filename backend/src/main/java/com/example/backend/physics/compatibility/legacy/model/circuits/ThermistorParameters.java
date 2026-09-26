package com.example.backend.physics.compatibility.legacy.model.circuits;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Beta-parameter NTC thermistor and divider response. */
public record ThermistorParameters(double referenceResistance, double referenceTemperature,
                                   double betaConstant, double temperature,
                                   double supplyVoltage, double dividerResistance) {
    private static final double KELVIN_OFFSET = 273.15;
    public static ThermistorParameters from(JsonNode specification, Map<String, Double> overrides) {
        double r0 = PhysicsValues.require(specification, overrides, "reference_resistance");
        double t0 = PhysicsValues.require(specification, overrides, "reference_temperature");
        double beta = PhysicsValues.require(specification, overrides, "beta_constant");
        double temperature = PhysicsValues.require(specification, overrides, "temperature");
        double supply = PhysicsValues.require(specification, overrides, "supply_voltage");
        double divider = PhysicsValues.require(specification, overrides, "divider_resistance");
        if (r0 <= 0 || t0 <= 0 || beta <= 0 || temperature <= -KELVIN_OFFSET
                || supply <= 0 || divider <= 0) {
            throw new IllegalArgumentException("Thermistor parameters are invalid");
        }
        return new ThermistorParameters(r0, t0, beta, temperature, supply, divider);
    }
    public double resistance() {
        double t = temperature + KELVIN_OFFSET;
        return referenceResistance * Math.exp(betaConstant * (1 / t - 1 / referenceTemperature));
    }
    public double dividerVoltage() { return supplyVoltage * dividerResistance / (dividerResistance + resistance()); }
    public double sensorPower() { return supplyVoltage * supplyVoltage / Math.pow(dividerResistance + resistance(), 2) * resistance(); }
}
