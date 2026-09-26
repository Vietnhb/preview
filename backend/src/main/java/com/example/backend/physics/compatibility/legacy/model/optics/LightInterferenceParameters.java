package com.example.backend.physics.compatibility.legacy.model.optics;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Two-coherent-source intensity interference at a point. */
public record LightInterferenceParameters(double wavelength, double pathDifference, double referenceIntensity) {
    public static LightInterferenceParameters from(JsonNode specification, Map<String, Double> overrides) {
        double wavelength = PhysicsValues.require(specification, overrides, "wavelength");
        double path = PhysicsValues.require(specification, overrides, "path_difference");
        double intensity = PhysicsValues.require(specification, overrides, "reference_intensity");
        if (wavelength <= 0 || !Double.isFinite(path) || intensity < 0) throw new IllegalArgumentException("Wavelength positive, path difference finite and intensity non-negative required");
        return new LightInterferenceParameters(wavelength, path, intensity);
    }
    public double phaseDifference() { return 2 * Math.PI * pathDifference / wavelength; }
    public double intensity() { return referenceIntensity * Math.pow(Math.cos(phaseDifference() / 2), 2); }
}
