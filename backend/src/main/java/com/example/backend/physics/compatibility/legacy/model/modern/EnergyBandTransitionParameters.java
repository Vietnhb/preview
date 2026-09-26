package com.example.backend.physics.compatibility.legacy.model.modern;

import com.example.backend.physics.model.PhysicalConstants;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Simplified band-gap photon transition model for semiconductor lessons. */
public record EnergyBandTransitionParameters(double valenceBandEnergy, double conductionBandEnergy,
                                              double photonFrequency) {
    public static EnergyBandTransitionParameters from(JsonNode specification, Map<String, Double> overrides) {
        double valence = PhysicsValues.require(specification, overrides, "valence_band_energy");
        double conduction = PhysicsValues.require(specification, overrides, "conduction_band_energy");
        double frequency = PhysicsValues.require(specification, overrides, "photon_frequency");
        if (conduction <= valence || frequency <= 0) {
            throw new IllegalArgumentException("Band energies and photon frequency are invalid");
        }
        return new EnergyBandTransitionParameters(valence, conduction, frequency);
    }
    public double bandGap() { return conductionBandEnergy - valenceBandEnergy; }
    public double photonEnergy() { return PhysicalConstants.PLANCK * photonFrequency; }
    public double thresholdWavelength() { return PhysicalConstants.PLANCK * PhysicalConstants.SPEED_OF_LIGHT / bandGap(); }
    public double transitionAllowed() { return photonEnergy() >= bandGap() ? 1 : 0; }
}
