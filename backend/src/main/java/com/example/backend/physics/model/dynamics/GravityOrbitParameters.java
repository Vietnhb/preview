package com.example.backend.physics.model.dynamics;

import com.example.backend.physics.model.PhysicalConstants;
import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Newtonian gravity and circular-orbit quantities. */
public record GravityOrbitParameters(double centralMass, double satelliteMass, double orbitRadius) {
    public static GravityOrbitParameters from(JsonNode specification, Map<String, Double> overrides) {
        double central = PhysicsValues.require(specification, overrides, "central_mass");
        double satellite = PhysicsValues.require(specification, overrides, "satellite_mass");
        double radius = PhysicsValues.require(specification, overrides, "orbit_radius");
        if (!(central > 0 && satellite > 0 && radius > 0)) throw new IllegalArgumentException("Masses and orbit radius must be positive");
        return new GravityOrbitParameters(central, satellite, radius);
    }
    public double gravitationalForce() { return PhysicalConstants.GRAVITATIONAL_CONSTANT * centralMass * satelliteMass / (orbitRadius * orbitRadius); }
    public double gravitationalField() { return PhysicalConstants.GRAVITATIONAL_CONSTANT * centralMass / (orbitRadius * orbitRadius); }
    public double orbitalSpeed() { return Math.sqrt(PhysicalConstants.GRAVITATIONAL_CONSTANT * centralMass / orbitRadius); }
    public double orbitalPeriod() { return 2 * Math.PI * Math.sqrt(Math.pow(orbitRadius, 3) / (PhysicalConstants.GRAVITATIONAL_CONSTANT * centralMass)); }
}
