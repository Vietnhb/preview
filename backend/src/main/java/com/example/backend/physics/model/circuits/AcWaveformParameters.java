package com.example.backend.physics.model.circuits;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.CanonicalQuantityBag;


/** Ideal sinusoidal AC voltage source. */
public record AcWaveformParameters(double peakVoltage, double frequency, double phase) {

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
