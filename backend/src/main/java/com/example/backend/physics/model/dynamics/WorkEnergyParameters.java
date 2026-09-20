package com.example.backend.physics.model.dynamics;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Canonical inputs for the constant-force work-energy experiment. */
public record WorkEnergyParameters(double mass, double initialSpeed, double finalSpeed,
                                   double force, double displacement, double forceAngle,
                                   double duration) {
    public static WorkEnergyParameters from(JsonNode specification, Map<String, Double> overrides) {
        double mass = PhysicsValues.require(specification, overrides, "mass");
        double initialSpeed = PhysicsValues.require(specification, overrides, "initial_speed");
        double finalSpeed = PhysicsValues.require(specification, overrides, "final_speed");
        double force = PhysicsValues.require(specification, overrides, "force");
        double displacement = PhysicsValues.require(specification, overrides, "displacement");
        double angle = PhysicsValues.require(specification, overrides, "force_angle");
        double duration = PhysicsValues.require(specification, overrides, "duration");
        if (!(mass > 0) || initialSpeed < 0 || finalSpeed < 0 || force < 0 || displacement < 0
                || duration <= 0 || !Double.isFinite(angle) || angle < 0 || angle > Math.PI) {
            throw new IllegalArgumentException("Invalid work-energy inputs");
        }
        return new WorkEnergyParameters(mass, initialSpeed, finalSpeed, force, displacement, angle, duration);
    }
    public double workByForce() { return force * displacement * Math.cos(forceAngle); }
    public double initialKineticEnergy() { return 0.5 * mass * initialSpeed * initialSpeed; }
    public double finalKineticEnergy() { return 0.5 * mass * finalSpeed * finalSpeed; }
    public double deltaKineticEnergy() { return finalKineticEnergy() - initialKineticEnergy(); }
    public double averagePower() { return workByForce() / duration; }
}
