package com.example.backend.physics.model.dynamics;

import com.example.backend.physics.model.PhysicsValues;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Canonical parameters for a harmonically forced oscillator in all damping
 * regimes.
 */
public record DampedForcedOscillationParameters(
        double mass,
        double springConstant,
        double dampingCoefficient,
        double drivingAmplitude,
        double drivingFrequency,
        double initialDisplacement,
        double initialVelocity) {

    public static DampedForcedOscillationParameters from(JsonNode specification,
            Map<String, Double> overrides) {
        double mass = PhysicsValues.require(specification, overrides, "mass");
        double spring = PhysicsValues.require(specification, overrides, "spring_constant");
        double damping = PhysicsValues.require(specification, overrides, "damping_coefficient");
        double driveAmplitude = PhysicsValues.require(specification, overrides, "driving_force_amplitude");
        double driveFrequency = PhysicsValues.require(specification, overrides, "driving_frequency");
        double x0 = PhysicsValues.require(specification, overrides, "initial_displacement");
        double v0 = PhysicsValues.require(specification, overrides, "initial_velocity");
        if (!(mass > 0) || !(spring > 0) || damping < 0 || driveAmplitude < 0 || driveFrequency < 0
                || !Double.isFinite(x0) || !Double.isFinite(v0)) {
            throw new IllegalArgumentException("Oscillator parameters must be finite and within physical bounds");
        }
        return new DampedForcedOscillationParameters(mass, spring, damping, driveAmplitude,
                driveFrequency, x0, v0);
    }

    public double naturalAngularFrequency() {
        return Math.sqrt(springConstant / mass);
    }

    public double dampedAngularFrequency() {
        return Math.sqrt(Math.max(0, naturalAngularFrequency() * naturalAngularFrequency()
                - dampingCoefficient * dampingCoefficient));
    }

    public double forceAmplitudePerMass() {
        return drivingAmplitude / mass;
    }

    public double steadyStateAmplitude() {
        double w0 = naturalAngularFrequency();
        double w = drivingFrequency;
        return forceAmplitudePerMass() / Math.sqrt(
                Math.pow(w0 * w0 - w * w, 2) + Math.pow(2 * dampingCoefficient * w, 2));
    }

    public double steadyStatePhase() {
        double w0 = naturalAngularFrequency();
        return Math.atan2(2 * dampingCoefficient * drivingFrequency,
                w0 * w0 - drivingFrequency * drivingFrequency);
    }

    public State stateAt(double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
            throw new IllegalArgumentException("Time must be finite and non-negative");
        }
        double w = drivingFrequency;
        double amplitude = steadyStateAmplitude();
        double phase = steadyStatePhase();
        double xParticular = amplitude * Math.cos(w * timeSeconds - phase);
        double vParticular = -amplitude * w * Math.sin(w * timeSeconds - phase);
        double aParticular = -amplitude * w * w * Math.cos(w * timeSeconds - phase);

        double c = initialDisplacement - amplitude * Math.cos(phase);
        double velocityDifference = initialVelocity - amplitude * w * Math.sin(phase);
        double w0 = naturalAngularFrequency();
        double h;
        double hVelocity;
        double hAcceleration;
        double criticalTolerance = 1e-12 * Math.max(1, w0);
        if (Math.abs(dampingCoefficient - w0) <= criticalTolerance) {
            // Repeated root r=-gamma: h=e^(-gamma t)(C+D t).
            double envelope = Math.exp(-dampingCoefficient * timeSeconds);
            double d = velocityDifference + dampingCoefficient * c;
            h = envelope * (c + d * timeSeconds);
            hVelocity = envelope * (d - dampingCoefficient * (c + d * timeSeconds));
            hAcceleration = -2 * dampingCoefficient * hVelocity - w0 * w0 * h;
        } else if (dampingCoefficient < w0) {
            double wd = Math.sqrt(w0 * w0 - dampingCoefficient * dampingCoefficient);
            double d = (velocityDifference + dampingCoefficient * c) / wd;
            double envelope = Math.exp(-dampingCoefficient * timeSeconds);
            double cosine = Math.cos(wd * timeSeconds);
            double sine = Math.sin(wd * timeSeconds);
            h = envelope * (c * cosine + d * sine);
            hVelocity = envelope * ((d * wd - dampingCoefficient * c) * cosine
                    + (-c * wd - dampingCoefficient * d) * sine);
            hAcceleration = -2 * dampingCoefficient * hVelocity - w0 * w0 * h;
        } else {
            // Over-damped roots r1/r2 are real and distinct.
            double root = Math.sqrt(dampingCoefficient * dampingCoefficient - w0 * w0);
            double r1 = -dampingCoefficient + root;
            double r2 = -dampingCoefficient - root;
            double c1 = (velocityDifference - r2 * c) / (r1 - r2);
            double c2 = c - c1;
            double e1 = Math.exp(r1 * timeSeconds);
            double e2 = Math.exp(r2 * timeSeconds);
            h = c1 * e1 + c2 * e2;
            hVelocity = r1 * c1 * e1 + r2 * c2 * e2;
            hAcceleration = -2 * dampingCoefficient * hVelocity - w0 * w0 * h;
        }
        return new State(xParticular + h, vParticular + hVelocity, aParticular + hAcceleration);
    }

    public record State(double displacement, double velocity, double acceleration) {
    }
}
