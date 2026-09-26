package com.example.backend.physics.compatibility.legacy.model.circuits;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Ideal transformer turns/voltage/current ratios. */
public record TransformerParameters(double primaryTurns, double secondaryTurns,
                                    double primaryVoltage, double secondaryCurrent) {
    public static TransformerParameters from(JsonNode specification, Map<String, Double> overrides) {
        double np = PhysicsValues.require(specification, overrides, "primary_turns");
        double ns = PhysicsValues.require(specification, overrides, "secondary_turns");
        double vp = PhysicsValues.require(specification, overrides, "primary_voltage");
        double is = PhysicsValues.require(specification, overrides, "secondary_current");
        if (np <= 0 || ns <= 0 || !Double.isFinite(vp) || !Double.isFinite(is)) throw new IllegalArgumentException("Transformer turns positive and voltage/current finite required");
        return new TransformerParameters(np, ns, vp, is);
    }
    public double turnsRatio() { return secondaryTurns / primaryTurns; }
    public double secondaryVoltage() { return primaryVoltage * turnsRatio(); }
    public double primaryCurrent() { return secondaryCurrent * turnsRatio(); }
}
