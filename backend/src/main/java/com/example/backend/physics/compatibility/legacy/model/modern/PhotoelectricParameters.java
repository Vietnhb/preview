package com.example.backend.physics.compatibility.legacy.model.modern;

import com.example.backend.physics.model.PhysicalConstants;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Canonical inputs for Einstein's photoelectric equation. */
public record PhotoelectricParameters(double photonFrequency, double workFunction, double electronCharge) {
    public static final double PLANCK = PhysicalConstants.PLANCK;
    public static final double SPEED_OF_LIGHT = PhysicalConstants.SPEED_OF_LIGHT;
    public static PhotoelectricParameters from(JsonNode specification, Map<String, Double> overrides) {
        double f = PhysicsValues.require(specification, overrides, "photon_frequency");
        double phi = PhysicsValues.require(specification, overrides, "work_function");
        double charge = PhysicsValues.optional(specification, overrides, PhysicalConstants.ELEMENTARY_CHARGE,
                "electron_charge");
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
