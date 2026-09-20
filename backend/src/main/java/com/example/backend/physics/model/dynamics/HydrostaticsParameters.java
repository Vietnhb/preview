package com.example.backend.physics.model.dynamics;

import com.example.backend.physics.model.PhysicalConstants;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.LegacySpecificationAdapter;
import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Gauge pressure and Archimedes buoyancy in a quiescent fluid. */
public record HydrostaticsParameters(double fluidDensity, double depth, double displacedVolume,
        double gravity, double atmosphericPressure) {
    public static HydrostaticsParameters from(JsonNode specification, Map<String, Double> overrides) {
        return from(LegacySpecificationAdapter.adapt(specification, overrides));
    }

    public static HydrostaticsParameters from(CanonicalQuantityBag quantities) {
        double density = quantities.require("fluid_density");
        double depth = quantities.require("depth");
        double volume = quantities.require("displaced_volume");
        double gravity = quantities.optional("gravitational_acceleration", PhysicalConstants.STANDARD_GRAVITY);
        double atmosphere = quantities.optional("atmospheric_pressure", PhysicalConstants.STANDARD_ATMOSPHERIC_PRESSURE);
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
