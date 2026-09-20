package com.example.backend.physics.model.circuits;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Ideal DC Ohm-law element with a prescribed voltage and resistance. */
public record OhmsLawParameters(double voltage, double resistance) {
    public static OhmsLawParameters from(JsonNode specification, Map<String, Double> overrides) {
        double voltage = PhysicsValues.require(specification, overrides, "voltage");
        double resistance = PhysicsValues.require(specification, overrides, "resistance");
        if (!Double.isFinite(voltage) || !(resistance > 0)) throw new IllegalArgumentException("Voltage finite and resistance positive required");
        return new OhmsLawParameters(voltage, resistance);
    }
    public double current() { return voltage / resistance; }
    public double power() { return voltage * current(); }
}
