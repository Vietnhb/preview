package com.example.backend.physics.compatibility.legacy.model.circuits;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Ideal capacitor charge and stored electrostatic energy. */
public record CapacitorParameters(double capacitance, double voltage) {
    public static CapacitorParameters from(JsonNode specification, Map<String, Double> overrides) {
        double c = PhysicsValues.require(specification, overrides, "capacitance");
        double v = PhysicsValues.require(specification, overrides, "voltage");
        if (!(c > 0) || !Double.isFinite(v)) throw new IllegalArgumentException("Capacitance positive and voltage finite required");
        return new CapacitorParameters(c, v);
    }
    public double charge() { return capacitance * voltage; }
    public double energy() { return .5 * capacitance * voltage * voltage; }
}
