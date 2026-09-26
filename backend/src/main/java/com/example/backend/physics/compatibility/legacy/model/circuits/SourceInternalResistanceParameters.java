package com.example.backend.physics.compatibility.legacy.model.circuits;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Practical source model: emf, internal resistance and an external load. */
public record SourceInternalResistanceParameters(double emf, double internalResistance,
                                                 double loadResistance) {
    public static SourceInternalResistanceParameters from(JsonNode specification, Map<String, Double> overrides) {
        double emf = PhysicsValues.require(specification, overrides, "emf");
        double internal = PhysicsValues.require(specification, overrides, "internal_resistance");
        double load = PhysicsValues.require(specification, overrides, "load_resistance");
        if (emf <= 0 || internal <= 0 || load <= 0) {
            throw new IllegalArgumentException("Source emf and resistances are invalid");
        }
        return new SourceInternalResistanceParameters(emf, internal, load);
    }
    public double current() { return emf / (internalResistance + loadResistance); }
    public double terminalVoltage() { return current() * loadResistance; }
    public double loadPower() { return current() * current() * loadResistance; }
    public double internalPowerLoss() { return current() * current() * internalResistance; }
    public double efficiency() { return terminalVoltage() / emf; }
}
