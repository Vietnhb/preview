package com.example.backend.physics.compatibility.legacy.model.optics;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Canonical normal-adjustment model for an astronomical telescope. */
public record AstronomicalTelescopeParameters(double objectiveFocalLength, double eyepieceFocalLength) {
    public static AstronomicalTelescopeParameters from(JsonNode specification, Map<String, Double> overrides) {
        double objective = PhysicsValues.require(specification, overrides, "objective_focal_length");
        double eyepiece = PhysicsValues.require(specification, overrides, "eyepiece_focal_length");
        if (!(objective > 0) || !(eyepiece > 0)
                || !Double.isFinite(objective) || !Double.isFinite(eyepiece)) {
            throw new IllegalArgumentException("Telescope focal lengths must be positive and finite");
        }
        return new AstronomicalTelescopeParameters(objective, eyepiece);
    }

    /** Magnitude; the negative sign for image inversion is exposed separately. */
    public double angularMagnification() { return objectiveFocalLength / eyepieceFocalLength; }
    public double signedAngularMagnification() { return -angularMagnification(); }
    public double normalAdjustmentLength() { return objectiveFocalLength + eyepieceFocalLength; }
}
