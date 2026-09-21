package com.example.backend.physics.compatibility.legacy.model.circuits;

import com.example.backend.physics.model.PhysicalConstants;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Shockley ideal-diode characteristic at a fixed temperature. */
public record DiodeParameters(double voltage, double saturationCurrent,
        double idealityFactor, double temperature) {

    public static DiodeParameters from(JsonNode specification, Map<String, Double> overrides) {
        double voltage = PhysicsValues.require(specification, overrides, "voltage");
        double saturation = PhysicsValues.require(specification, overrides, "saturation_current");
        double ideality = PhysicsValues.require(specification, overrides, "ideality_factor");
        double temperature = PhysicsValues.require(specification, overrides, "temperature");
        if (!(saturation > 0 && ideality > 0 && temperature > 0) || !Double.isFinite(voltage))
            throw new IllegalArgumentException("Invalid diode inputs");
        return new DiodeParameters(voltage, saturation, ideality, temperature);
    }

    public double thermalVoltage() {
        return PhysicalConstants.BOLTZMANN * temperature / PhysicalConstants.ELEMENTARY_CHARGE;
    }

    public double current() {
        double exponent = Math.min(700, voltage / (idealityFactor * thermalVoltage()));
        return saturationCurrent * Math.expm1(exponent);
    }

    public double power() {
        return voltage * current();
    }
}
