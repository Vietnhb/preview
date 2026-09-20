package com.example.backend.physics.model.waves;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Canonical two-source water-surface interference inputs on a square grid. */
public record WaterSurfaceInterferenceParameters(double wavelength, double waveSpeed,
                                                 double sourceSeparation, double amplitude,
                                                 double domainSize, int spatialSamples) {
    public static WaterSurfaceInterferenceParameters from(JsonNode specification, Map<String, Double> overrides) {
        double wavelength = PhysicsValues.require(specification, overrides, "wavelength");
        double speed = PhysicsValues.require(specification, overrides, "wave_speed");
        double separation = PhysicsValues.require(specification, overrides, "source_separation");
        double amplitude = PhysicsValues.require(specification, overrides, "amplitude");
        double domain = PhysicsValues.require(specification, overrides, "domain_size");
        double samples = PhysicsValues.require(specification, overrides, "spatial_samples");
        if (!(wavelength > 0) || !(speed > 0) || !(separation > 0) || !(amplitude >= 0) || !(domain > 0)
                || !Double.isFinite(wavelength) || !Double.isFinite(speed) || !Double.isFinite(separation)
                || !Double.isFinite(amplitude) || !Double.isFinite(domain)
                || samples != Math.rint(samples) || samples < 8 || samples > 128) {
            throw new IllegalArgumentException("Water-surface parameters are invalid; samples must be an integer in [8,128]");
        }
        if (separation >= domain) throw new IllegalArgumentException("Source separation must be smaller than the domain size");
        return new WaterSurfaceInterferenceParameters(wavelength, speed, separation, amplitude, domain, (int) samples);
    }

    public double waveNumber() { return 2.0 * Math.PI / wavelength; }
    public double angularFrequency() { return 2.0 * Math.PI * waveSpeed / wavelength; }
}
