package com.example.backend.physics.compatibility.legacy.model.waves;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** AM/FM teaching chain with carrier, sidebands and dB path loss. */
public record RadioSignalChainParameters(double carrierFrequency, double modulationFrequency,
                                         double frequencyDeviation, double modulationIndex,
                                         double signalAmplitude, double pathLength,
                                         double attenuationDbPerMeter) {
    private static final double LIGHT_SPEED = 299_792_458;

    public static RadioSignalChainParameters from(JsonNode specification, Map<String, Double> overrides) {
        double carrier = PhysicsValues.require(specification, overrides, "carrier_frequency");
        double modulation = PhysicsValues.require(specification, overrides, "modulation_frequency");
        double deviation = PhysicsValues.require(specification, overrides, "frequency_deviation");
        double index = PhysicsValues.require(specification, overrides, "modulation_index");
        double amplitude = PhysicsValues.require(specification, overrides, "signal_amplitude");
        double distance = PhysicsValues.require(specification, overrides, "path_length");
        double attenuation = PhysicsValues.require(specification, overrides, "attenuation_db_per_meter");
        if (carrier <= 0 || modulation <= 0 || deviation < 0 || index < 0 || amplitude < 0
                || distance < 0 || attenuation < 0 || carrier <= modulation) {
            throw new IllegalArgumentException("Radio signal-chain parameters are invalid");
        }
        return new RadioSignalChainParameters(carrier, modulation, deviation, index, amplitude, distance, attenuation);
    }
    public double wavelength() { return LIGHT_SPEED / carrierFrequency; }
    public double lowerSideband() { return carrierFrequency - modulationFrequency; }
    public double upperSideband() { return carrierFrequency + modulationFrequency; }
    public double fmModulationIndex() { return frequencyDeviation / modulationFrequency; }
    public double carsonBandwidth() { return 2 * (frequencyDeviation + modulationFrequency); }
    public double attenuationFactor() { return Math.pow(10, -(attenuationDbPerMeter * pathLength) / 20); }
    public double receivedAmplitude() { return signalAmplitude * attenuationFactor(); }
}
