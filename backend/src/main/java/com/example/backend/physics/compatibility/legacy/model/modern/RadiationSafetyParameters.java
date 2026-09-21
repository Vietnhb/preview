package com.example.backend.physics.compatibility.legacy.model.modern;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Inverse-square point-source dose-rate teaching model. */
public record RadiationSafetyParameters(double referenceDoseRate, double referenceDistance, double distance) {
    public static RadiationSafetyParameters from(JsonNode specification, Map<String, Double> overrides) {
        double dose = PhysicsValues.require(specification, overrides, "reference_dose_rate");
        double reference = PhysicsValues.require(specification, overrides, "reference_distance");
        double distance = PhysicsValues.require(specification, overrides, "distance");
        if (dose < 0 || !(reference > 0) || !(distance > 0)) throw new IllegalArgumentException("Dose non-negative and distances positive required");
        return new RadiationSafetyParameters(dose, reference, distance);
    }
    public double doseRate() { return referenceDoseRate * Math.pow(referenceDistance / distance, 2); }
}
