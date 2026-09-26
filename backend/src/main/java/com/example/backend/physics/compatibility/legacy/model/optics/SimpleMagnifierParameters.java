package com.example.backend.physics.compatibility.legacy.model.optics;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Canonical inputs for a simple magnifying glass with a virtual image. */
public record SimpleMagnifierParameters(double focalLength, double objectDistance, double nearPoint) {
    public static SimpleMagnifierParameters from(JsonNode specification, Map<String, Double> overrides) {
        double focal = PhysicsValues.require(specification, overrides, "focal_length");
        double object = PhysicsValues.require(specification, overrides, "object_distance");
        double near = PhysicsValues.require(specification, overrides, "near_point");
        if (focal <= 0 || object <= 0 || near <= 0
                || !Double.isFinite(focal) || !Double.isFinite(object) || !Double.isFinite(near)
                || object >= focal) {
            throw new IllegalArgumentException("Magnifier requires 0 < object distance < focal length and positive near point");
        }
        return new SimpleMagnifierParameters(focal, object, near);
    }

    /** Magnitude of the virtual image distance behind the lens. */
    public double virtualImageDistance() { return focalLength * objectDistance / (focalLength - objectDistance); }
    public double linearMagnification() { return focalLength / (focalLength - objectDistance); }
    public double relaxedAngularMagnification() { return nearPoint / focalLength; }
    public double nearPointAngularMagnification() { return 1.0 + nearPoint / focalLength; }
}
