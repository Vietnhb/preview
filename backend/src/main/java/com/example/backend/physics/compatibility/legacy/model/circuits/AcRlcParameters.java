package com.example.backend.physics.compatibility.legacy.model.circuits;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/** Canonical RMS parameters for a series AC RLC circuit. */
public record AcRlcParameters(double resistance, double inductance, double capacitance,
        double frequency, double rmsVoltage) {
    public static AcRlcParameters from(JsonNode specification, Map<String, Double> overrides) {
        double r = PhysicsValues.require(specification, overrides, "resistance");
        double l = PhysicsValues.require(specification, overrides, "inductance");
        double c = PhysicsValues.require(specification, overrides, "capacitance");
        double f = PhysicsValues.require(specification, overrides, "frequency");
        double v = PhysicsValues.require(specification, overrides, "rms_voltage");
        if (!(r > 0 && l > 0 && c > 0 && f > 0 && v >= 0)) {
            throw new IllegalArgumentException("R, L, C, frequency must be positive and RMS voltage non-negative");
        }
        return new AcRlcParameters(r, l, c, f, v);
    }

    public double angularFrequency() {
        return 2 * Math.PI * frequency;
    }

    public double inductiveReactance() {
        return angularFrequency() * inductance;
    }

    public double capacitiveReactance() {
        return 1.0 / (angularFrequency() * capacitance);
    }

    public double impedance() {
        return Math.hypot(resistance, inductiveReactance() - capacitiveReactance());
    }

    public double rmsCurrent() {
        return rmsVoltage / impedance();
    }

    public double powerFactor() {
        return resistance / impedance();
    }

    public double realPower() {
        return rmsCurrent() * rmsCurrent() * resistance;
    }

    public double phase() {
        return Math.atan2(inductiveReactance() - capacitiveReactance(), resistance);
    }
}
