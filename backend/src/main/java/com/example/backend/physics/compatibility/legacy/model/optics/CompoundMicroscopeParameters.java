package com.example.backend.physics.compatibility.legacy.model.optics;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Canonical paraxial model for a compound microscope focused at infinity or near point. */
public record CompoundMicroscopeParameters(double objectiveFocalLength, double eyepieceFocalLength,
                                           double tubeLength, double nearPoint) {
    public static CompoundMicroscopeParameters from(JsonNode specification, Map<String, Double> overrides) {
        double objective = PhysicsValues.require(specification, overrides, "objective_focal_length");
        double eyepiece = PhysicsValues.require(specification, overrides, "eyepiece_focal_length");
        double tube = PhysicsValues.require(specification, overrides, "tube_length");
        double near = PhysicsValues.require(specification, overrides, "near_point");
        if (!(objective > 0) || !(eyepiece > 0) || !(tube > 0) || !(near > 0)
                || !Double.isFinite(objective) || !Double.isFinite(eyepiece)
                || !Double.isFinite(tube) || !Double.isFinite(near)) {
            throw new IllegalArgumentException("Microscope focal lengths, tube length and near point must be positive and finite");
        }
        return new CompoundMicroscopeParameters(objective, eyepiece, tube, near);
    }

    public double objectiveMagnification() { return tubeLength / objectiveFocalLength; }
    public double relaxedAngularMagnification() { return objectiveMagnification() * nearPoint / eyepieceFocalLength; }
    public double nearPointAngularMagnification() {
        return objectiveMagnification() * (1.0 + nearPoint / eyepieceFocalLength);
    }
}
