package com.example.backend.physics.model.dynamics;

import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.LegacySpecificationAdapter;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Static linear-elastic (Hooke-law) spring deformation. */
public record HookeLawParameters(double springConstant, double displacement) {
    public static HookeLawParameters from(JsonNode specification, Map<String, Double> overrides) {
        return from(LegacySpecificationAdapter.adapt(specification, overrides));
    }

    public static HookeLawParameters from(CanonicalQuantityBag quantities) {
        double k = quantities.require("spring_constant");
        double x = quantities.require("displacement");
        if (!(k > 0) || !Double.isFinite(x))
            throw new IllegalArgumentException("Spring constant and displacement are invalid");
        return new HookeLawParameters(k, x);
    }

    public double restoringForce() {
        return -springConstant * displacement;
    }

    public double elasticPotentialEnergy() {
        return 0.5 * springConstant * displacement * displacement;
    }
}
