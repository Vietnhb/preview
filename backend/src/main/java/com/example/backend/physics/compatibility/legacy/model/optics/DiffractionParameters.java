package com.example.backend.physics.compatibility.legacy.model.optics;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Single-slit diffraction minima and Malus-law polarization intensity. */
public record DiffractionParameters(double wavelength, double slitWidth, double order,
                                    double inputIntensity, double analyzerAngle) {
    public static DiffractionParameters from(JsonNode specification, Map<String, Double> overrides) {
        double wavelength = PhysicsValues.require(specification, overrides, "wavelength");
        double width = PhysicsValues.require(specification, overrides, "slit_width");
        double order = PhysicsValues.require(specification, overrides, "diffraction_order");
        double intensity = PhysicsValues.require(specification, overrides, "input_intensity");
        double angle = PhysicsValues.require(specification, overrides, "analyzer_angle");
        if (!(wavelength > 0) || !(width > 0) || order < 0 || order != Math.rint(order) || intensity < 0 || !Double.isFinite(angle))
            throw new IllegalArgumentException("Diffraction geometry/order and polarization inputs are invalid");
        return new DiffractionParameters(wavelength, width, order, intensity, angle);
    }
    public double sineAngle() { return order * wavelength / slitWidth; }
    public boolean minimumExists() { return sineAngle() <= 1; }
    public double diffractionAngle() { return minimumExists() ? Math.asin(sineAngle()) : 0; }
    public double transmittedIntensity() { return inputIntensity * Math.pow(Math.cos(analyzerAngle), 2); }
}
