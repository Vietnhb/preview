package com.example.backend.physics.compatibility.legacy.model.modern;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Mass-balance model for fission/fusion reaction energy. */
public record NuclearReactionParameters(double reactantMass, double productMass, double reactionCount) {
    private static final double C = 299_792_458;
    public static NuclearReactionParameters from(JsonNode specification, Map<String, Double> overrides) {
        double reactant = PhysicsValues.require(specification, overrides, "reactant_mass");
        double product = PhysicsValues.require(specification, overrides, "product_mass");
        double count = PhysicsValues.optional(specification, overrides, 1, "reaction_count");
        if (!(reactant > 0) || !(product > 0) || !(count > 0) || product > reactant) {
            throw new IllegalArgumentException("Nuclear reaction masses or count are invalid");
        }
        return new NuclearReactionParameters(reactant, product, count);
    }
    public double massDefect() { return reactantMass - productMass; }
    public double releasedEnergyPerReaction() { return massDefect() * C * C; }
    public double totalReleasedEnergy() { return releasedEnergyPerReaction() * reactionCount; }
}
