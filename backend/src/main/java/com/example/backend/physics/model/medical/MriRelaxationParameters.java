package com.example.backend.physics.model.medical;

import com.example.backend.physics.model.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Bloch relaxation approximation used by a teaching MRI pulse sequence. */
public record MriRelaxationParameters(double equilibriumMagnetization, double longitudinalTime,
                                      double transverseTime, double echoTime) {
    public static MriRelaxationParameters from(JsonNode specification, Map<String, Double> overrides) {
        double m0 = PhysicsValues.require(specification, overrides, "equilibrium_magnetization");
        double t1 = PhysicsValues.require(specification, overrides, "longitudinal_relaxation_time");
        double t2 = PhysicsValues.require(specification, overrides, "transverse_relaxation_time");
        double echo = PhysicsValues.require(specification, overrides, "echo_time");
        if (!(m0 >= 0) || !(t1 > 0) || !(t2 > 0) || echo < 0) {
            throw new IllegalArgumentException("MRI relaxation parameters are invalid");
        }
        return new MriRelaxationParameters(m0, t1, t2, echo);
    }
    public double longitudinalMagnetization(double time) { return equilibriumMagnetization * (1 - Math.exp(-time / longitudinalTime)); }
    public double transverseMagnetization(double time) { return equilibriumMagnetization * Math.exp(-time / transverseTime); }
    public double echoSignal() { return transverseMagnetization(echoTime); }
}
