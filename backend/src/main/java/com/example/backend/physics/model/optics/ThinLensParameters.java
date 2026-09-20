package com.example.backend.physics.model.optics;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Canonical inputs for paraxial thin-lens imaging. */
public record ThinLensParameters(double focalLength, double objectDistance, double objectHeight) {
    public static ThinLensParameters from(JsonNode specification, Map<String, Double> overrides) {
        double focal = PhysicsValues.require(specification, overrides, "focal_length");
        double objectDistance = PhysicsValues.require(specification, overrides, "object_distance");
        double objectHeight = PhysicsValues.require(specification, overrides, "object_height");
        if (focal == 0 || objectDistance <= 0 || !Double.isFinite(focal)
                || !Double.isFinite(objectDistance) || !Double.isFinite(objectHeight)) {
            throw new IllegalArgumentException("Lens focal length must be finite and non-zero; object distance positive and height finite");
        }
        if (Math.abs(objectDistance - focal) < 1e-12) {
            throw new IllegalArgumentException("Object at focal point has no finite thin-lens image");
        }
        return new ThinLensParameters(focal, objectDistance, objectHeight);
    }

    public double imageDistance() { return focalLength * objectDistance / (objectDistance - focalLength); }
    public double magnification() { return -imageDistance() / objectDistance; }
    public double imageHeight() { return magnification() * objectHeight; }
}
