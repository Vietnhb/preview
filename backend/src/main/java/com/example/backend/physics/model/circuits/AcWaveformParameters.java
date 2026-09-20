package com.example.backend.physics.model.circuits;

import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.CanonicalQuantityBag;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Ideal sinusoidal AC voltage source. */
public record AcWaveformParameters(double peakVoltage, double frequency, double phase) {
    /** @deprecated Use {@link #from(CanonicalQuantityBag)} after schema-bound ingress. */
    @Deprecated
    public static AcWaveformParameters from(JsonNode specification, Map<String, Double> overrides) {
        return from(PhysicsValues.bag(specification, overrides));
    }

    public static AcWaveformParameters from(CanonicalQuantityBag quantities) {
        double peak = quantities.require("peak_voltage");
        double frequency = quantities.require("frequency");
        double phase = quantities.optional("phase", 0);
        if (peak < 0 || !(frequency > 0) || !Double.isFinite(phase)) throw new IllegalArgumentException("Peak voltage non-negative, frequency positive and phase finite required");
        return new AcWaveformParameters(peak, frequency, phase);
    }
    public double angularFrequency() { return 2 * Math.PI * frequency; }
    public double rmsVoltage() { return peakVoltage / Math.sqrt(2); }
    public double voltage(double time) { return peakVoltage * Math.sin(angularFrequency() * time + phase); }
}
