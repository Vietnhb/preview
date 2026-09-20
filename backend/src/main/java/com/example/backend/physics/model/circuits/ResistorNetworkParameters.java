package com.example.backend.physics.model.circuits;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Two-resistor ideal series/parallel network. */
public record ResistorNetworkParameters(double voltage, double resistance1, double resistance2) {
    public static ResistorNetworkParameters from(JsonNode specification, Map<String, Double> overrides) {
        double voltage = PhysicsValues.require(specification, overrides, "voltage");
        double r1 = PhysicsValues.require(specification, overrides, "resistance_1");
        double r2 = PhysicsValues.require(specification, overrides, "resistance_2");
        if (!Double.isFinite(voltage) || !(r1 > 0) || !(r2 > 0)) throw new IllegalArgumentException("Voltage finite and both resistances positive required");
        return new ResistorNetworkParameters(voltage, r1, r2);
    }
    public double seriesResistance() { return resistance1 + resistance2; }
    public double parallelResistance() { return resistance1 * resistance2 / (resistance1 + resistance2); }
}
