package com.example.backend.physics.compatibility.legacy.model.medical;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Single-ray CT projection model: line integral and Beer-Lambert transmission.
 */
public record CtReconstructionParameters(double incidentIntensity, double attenuationCoefficient,
        double pathLength, int projectionCount) {
    public static CtReconstructionParameters from(JsonNode specification, Map<String, Double> overrides) {
        double intensity = PhysicsValues.require(specification, overrides, "incident_intensity");
        double coefficient = PhysicsValues.require(specification, overrides, "attenuation_coefficient");
        double length = PhysicsValues.require(specification, overrides, "path_length");
        double projections = PhysicsValues.require(specification, overrides, "projection_count");
        if (intensity <= 0 || coefficient < 0 || length <= 0 || projections < 1
                || projections != Math.rint(projections)) {
            throw new IllegalArgumentException("CT projection parameters are invalid");
        }
        return new CtReconstructionParameters(intensity, coefficient, length, (int) projections);
    }

    public double lineIntegral() {
        return attenuationCoefficient * pathLength;
    }

    public double transmittedIntensity() {
        return incidentIntensity * Math.exp(-lineIntegral());
    }

    public double angularStep() {
        return 2 * Math.PI / projectionCount;
    }
}
