package com.example.backend.physics.model.dynamics;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Gauge pressure and Archimedes buoyancy in a quiescent fluid. */
public record HydrostaticsParameters(double fluidDensity, double depth, double displacedVolume,
        double gravity, double atmosphericPressure) {
    public static HydrostaticsParameters from(JsonNode specification, Map<String, Double> overrides) {
        double density = PhysicsValues.require(specification, overrides, "fluid_density");
        double depth = PhysicsValues.require(specification, overrides, "depth");
        double volume = PhysicsValues.require(specification, overrides, "displaced_volume");
        double gravity = PhysicsValues.optional(specification, overrides, 9.81, "gravitational_acceleration");
        double atmosphere = PhysicsValues.optional(specification, overrides, 101325, "atmospheric_pressure");
        if (!(density > 0) || depth < 0 || volume < 0 || !(gravity > 0) || atmosphere < 0) {
            throw new IllegalArgumentException("Invalid hydrostatics inputs");
        }
        return new HydrostaticsParameters(density, depth, volume, gravity, atmosphere);
    }

    public double gaugePressure() {
        return fluidDensity * gravity * depth;
    }

    public double absolutePressure() {
        return atmosphericPressure + gaugePressure();
    }

    public double buoyantForce() {
        return fluidDensity * gravity * displacedVolume;
    }
}
