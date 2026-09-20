package com.example.backend.physics.model.medical;

import com.example.backend.physics.model.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Beer-Lambert attenuation model for an X-ray diagnostic beam. */
public record XrayImagingParameters(double incidentIntensity, double attenuationCoefficient,
                                    double thickness, double exposureTime) {
    public static XrayImagingParameters from(JsonNode specification, Map<String, Double> overrides) {
        double intensity = PhysicsValues.require(specification, overrides, "incident_intensity");
        double coefficient = PhysicsValues.require(specification, overrides, "attenuation_coefficient");
        double thickness = PhysicsValues.require(specification, overrides, "material_thickness");
        double exposure = PhysicsValues.optional(specification, overrides, 1, "exposure_time");
        if (!(intensity > 0) || !(coefficient > 0) || thickness < 0 || !(exposure > 0)) {
            throw new IllegalArgumentException("X-ray intensity, attenuation and thickness are invalid");
        }
        return new XrayImagingParameters(intensity, coefficient, thickness, exposure);
    }
    public double transmittedIntensity() { return incidentIntensity * Math.exp(-attenuationCoefficient * thickness); }
    public double absorbedFraction() { return 1 - transmittedIntensity() / incidentIntensity; }
    public double detectorDoseProxy() { return transmittedIntensity() * exposureTime; }
    public double halfValueLayer() { return attenuationCoefficient == 0 ? Double.POSITIVE_INFINITY : Math.log(2) / attenuationCoefficient; }
}
