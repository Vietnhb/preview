package com.example.backend.physics.model.modern;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Mass-defect to released energy conversion. */
public record NuclearEnergyParameters(double massDefect, double reactionCount) {
    public static final double SPEED_OF_LIGHT = 299_792_458.0;
    public static NuclearEnergyParameters from(JsonNode specification, Map<String, Double> overrides) {
        double dm = PhysicsValues.require(specification, overrides, "mass_defect");
        double count = PhysicsValues.optional(specification, overrides, 1.0, "reaction_count");
        if (!(dm >= 0) || !(count > 0) || !Double.isFinite(dm)) {
            throw new IllegalArgumentException("Mass defect must be non-negative and reaction count positive");
        }
        return new NuclearEnergyParameters(dm, count);
    }
    public double releasedEnergy() { return massDefect * SPEED_OF_LIGHT * SPEED_OF_LIGHT * reactionCount; }
}
