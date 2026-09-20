package com.example.backend.physics.model.waves;

import com.example.backend.physics.model.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Carrier/modulation relationships for an ideal AM radio link. */
public record RadioCommunicationParameters(double carrierFrequency, double modulationFrequency,
                                           double modulationIndex) {
    private static final double C = 299_792_458.0;
    public static RadioCommunicationParameters from(JsonNode specification, Map<String, Double> overrides) {
        double carrier = PhysicsValues.require(specification, overrides, "carrier_frequency");
        double modulation = PhysicsValues.require(specification, overrides, "modulation_frequency");
        double index = PhysicsValues.require(specification, overrides, "modulation_index");
        if (!(carrier > 0 && modulation > 0) || !Double.isFinite(index) || index < 0 || index > 1)
            throw new IllegalArgumentException("Invalid radio carrier or modulation inputs");
        return new RadioCommunicationParameters(carrier, modulation, index);
    }
    public double wavelength() { return C / carrierFrequency; }
    public double period() { return 1 / carrierFrequency; }
    public double angularFrequency() { return 2 * Math.PI * carrierFrequency; }
    public double lowerSideband() { return carrierFrequency - modulationFrequency; }
    public double upperSideband() { return carrierFrequency + modulationFrequency; }
}
