package com.example.backend.physics.model.modern;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Canonical inputs for Einstein's photoelectric equation. */
public record PhotoelectricParameters(double photonFrequency, double workFunction, double electronCharge) {
    public static final double PLANCK = 6.62607015e-34;
    public static final double SPEED_OF_LIGHT = 299_792_458.0;
    public static PhotoelectricParameters from(JsonNode specification, Map<String, Double> overrides) {
        double f = PhysicsValues.require(specification, overrides, "photon_frequency");
        double phi = PhysicsValues.require(specification, overrides, "work_function");
        double charge = PhysicsValues.optional(specification, overrides, 1.602176634e-19, "electron_charge");
        if (!(f > 0) || phi < 0 || !(charge > 0) || !Double.isFinite(phi)) {
            throw new IllegalArgumentException("Frequency, work function and electron charge are invalid");
        }
        return new PhotoelectricParameters(f, phi, charge);
    }
    public double photonEnergy() { return PLANCK * photonFrequency; }
    /** True only when the incident photon is above the work-function threshold. */
    public boolean emissionOccurs() { return photonEnergy() >= workFunction; }
    public double maximumKineticEnergy() { return Math.max(0, photonEnergy() - workFunction); }
    public double stoppingPotential() { return maximumKineticEnergy() / electronCharge; }
    public double wavelength() { return SPEED_OF_LIGHT / photonFrequency; }
}
