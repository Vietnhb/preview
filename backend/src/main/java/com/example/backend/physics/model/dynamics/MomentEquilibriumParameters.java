package com.example.backend.physics.model.dynamics;

import com.example.backend.physics.model.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Planar torque balance for two applied forces about a pivot. */
public record MomentEquilibriumParameters(double force1, double arm1, double angle1,
                                          double force2, double arm2, double angle2) {
    public static MomentEquilibriumParameters from(JsonNode specification, Map<String, Double> overrides) {
        double f1 = PhysicsValues.require(specification, overrides, "force_1");
        double r1 = PhysicsValues.require(specification, overrides, "arm_1");
        double theta1 = PhysicsValues.require(specification, overrides, "angle_1");
        double f2 = PhysicsValues.require(specification, overrides, "force_2");
        double r2 = PhysicsValues.require(specification, overrides, "arm_2");
        double theta2 = PhysicsValues.require(specification, overrides, "angle_2");
        if (f1 < 0 || f2 < 0 || !(r1 >= 0) || !(r2 >= 0)
                || !Double.isFinite(theta1) || !Double.isFinite(theta2)) {
            throw new IllegalArgumentException("Moment parameters are invalid");
        }
        return new MomentEquilibriumParameters(f1, r1, theta1, f2, r2, theta2);
    }
    public double moment1() { return force1 * arm1 * Math.sin(angle1); }
    public double moment2() { return force2 * arm2 * Math.sin(angle2); }
    public double netMoment() { return moment1() + moment2(); }
    public double equilibriumResidual() { return Math.abs(netMoment()); }
}
