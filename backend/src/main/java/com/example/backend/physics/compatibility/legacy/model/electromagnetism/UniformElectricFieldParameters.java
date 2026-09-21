package com.example.backend.physics.compatibility.legacy.model.electromagnetism;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Uniform-field force and transverse acceleration between parallel plates. */
public record UniformElectricFieldParameters(double charge, double mass,
                                             double potentialDifference, double plateSeparation,
                                             double initialVelocity, double travelTime) {
    public static UniformElectricFieldParameters from(JsonNode specification, Map<String, Double> overrides) {
        double charge = PhysicsValues.require(specification, overrides, "charge");
        double mass = PhysicsValues.require(specification, overrides, "mass");
        double voltage = PhysicsValues.require(specification, overrides, "potential_difference");
        double separation = PhysicsValues.require(specification, overrides, "plate_separation");
        double velocity = PhysicsValues.require(specification, overrides, "initial_velocity");
        double time = PhysicsValues.require(specification, overrides, "travel_time");
        if (!(mass > 0) || separation <= 0 || voltage < 0 || velocity < 0 || time < 0 || !Double.isFinite(charge)) {
            throw new IllegalArgumentException("Uniform electric-field parameters are invalid");
        }
        return new UniformElectricFieldParameters(charge, mass, voltage, separation, velocity, time);
    }
    public double fieldStrength() { return potentialDifference / plateSeparation; }
    public double electricForce() { return charge * fieldStrength(); }
    public double acceleration() { return electricForce() / mass; }
    public double transverseDisplacement() { return 0.5 * acceleration() * travelTime * travelTime; }
    public double longitudinalDisplacement() { return initialVelocity * travelTime; }
}
