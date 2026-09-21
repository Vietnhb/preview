package com.example.backend.physics.compatibility.legacy.model.electromagnetism;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Lorentz magnetic force magnitude for a moving point charge. */
public record MagneticForceParameters(double charge, double speed, double magneticField, double angle) {
    public static MagneticForceParameters from(JsonNode specification, Map<String, Double> overrides) {
        double q = PhysicsValues.require(specification, overrides, "charge");
        double v = PhysicsValues.require(specification, overrides, "speed");
        double b = PhysicsValues.require(specification, overrides, "magnetic_field");
        double theta = PhysicsValues.require(specification, overrides, "velocity_field_angle");
        if (!Double.isFinite(q) || !(v >= 0) || !(b >= 0) || !Double.isFinite(theta) || theta < 0 || theta > Math.PI)
            throw new IllegalArgumentException("Invalid charge, speed, field or angle");
        return new MagneticForceParameters(q, v, b, theta);
    }
    public double forceMagnitude() { return Math.abs(charge) * speed * magneticField * Math.sin(angle); }
}
