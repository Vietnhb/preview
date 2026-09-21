package com.example.backend.physics.model.dynamics;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.CanonicalQuantityBag;


/** Static linear-elastic (Hooke-law) spring deformation. */
public record HookeLawParameters(double springConstant, double displacement) {

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
