package com.example.backend.physics.model.dynamics;

import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.PhysicalChecks;
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

    private static final double CRITICAL_ROOT_RELATIVE_TOLERANCE = 1.0e-12;

    /** @deprecated Use {@link #from(CanonicalQuantityBag)} after schema-bound ingress. */
    @Deprecated
    public static DampedForcedOscillationParameters from(JsonNode specification,
            Map<String, Double> overrides) {
        return from(PhysicsValues.bag(specification, overrides));
    }

    public static DampedForcedOscillationParameters from(CanonicalQuantityBag quantities) {
        double mass = quantities.require("mass");
        double spring = quantities.require("spring_constant");
        double damping = quantities.require("damping_coefficient");
        double driveAmplitude = quantities.require("driving_force_amplitude");
        double driveFrequency = quantities.require("driving_frequency");
        double x0 = quantities.require("initial_displacement");
        double v0 = quantities.require("initial_velocity");
        PhysicalChecks.positive(mass, "mass");
        PhysicalChecks.positive(spring, "spring_constant");
        PhysicalChecks.nonNegative(damping, "damping_coefficient");
        PhysicalChecks.nonNegative(driveAmplitude, "driving_force_amplitude");
        PhysicalChecks.nonNegative(driveFrequency, "driving_frequency");
        PhysicalChecks.finite(x0, "initial_displacement");
        PhysicalChecks.finite(v0, "initial_velocity");
        return new DampedForcedOscillationParameters(mass, spring, damping, driveAmplitude,
                driveFrequency, x0, v0);
    }

    public double naturalAngularFrequency() {
        return Math.sqrt(springConstant / mass);
    }

    /** Damping rate gamma = c/(2m) for m*x'' + c*x' + k*x = F(t). */
    public double dampingRate() {
        return dampingCoefficient / (2 * mass);
    }

    public double dampedAngularFrequency() {
        double gamma = dampingRate();
        return Math.sqrt(Math.max(0, naturalAngularFrequency() * naturalAngularFrequency() - gamma * gamma));
    }

    public double forceAmplitudePerMass() {
        return drivingAmplitude / mass;
    }

    public double steadyStateAmplitude() {
        double w0 = naturalAngularFrequency();
        double w = drivingFrequency;
        double gamma = dampingRate();
        double denominator = Math.sqrt(Math.pow(w0 * w0 - w * w, 2) + Math.pow(2 * gamma * w, 2));
        if (denominator == 0) {
            if (drivingAmplitude == 0) return 0;
            throw new IllegalArgumentException("Undamped resonant steady-state amplitude is unbounded");
        }
        return forceAmplitudePerMass() / denominator;
    }

    public double steadyStatePhase() {
        double w0 = naturalAngularFrequency();
        double gamma = dampingRate();
        return Math.atan2(2 * gamma * drivingFrequency,
                w0 * w0 - drivingFrequency * drivingFrequency);
    }

    public State stateAt(double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
            throw new IllegalArgumentException("Time must be finite and non-negative");
        }
        double w = drivingFrequency;
        double amplitude = steadyStateAmplitude();
        double gamma = dampingRate();
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
        // Compare the two frequencies on their own scale. A floor of 1 rad/s
        // incorrectly treats every sufficiently slow oscillator as critical.
        double rootScale = Math.max(Math.abs(gamma), Math.abs(w0));
        boolean criticallyDamped = rootScale > 0
                && Math.abs(gamma / rootScale - w0 / rootScale)
                <= CRITICAL_ROOT_RELATIVE_TOLERANCE;
        if (criticallyDamped) {
            // Repeated root r=-gamma: h=e^(-gamma t)(C+D t).
            double envelope = Math.exp(-gamma * timeSeconds);
            double d = velocityDifference + gamma * c;
            h = envelope * (c + d * timeSeconds);
            hVelocity = envelope * (d - gamma * (c + d * timeSeconds));
            hAcceleration = -2 * gamma * hVelocity - w0 * w0 * h;
        } else if (gamma < w0) {
            double wd = Math.sqrt(w0 * w0 - gamma * gamma);
            double d = (velocityDifference + gamma * c) / wd;
            double envelope = Math.exp(-gamma * timeSeconds);
            double cosine = Math.cos(wd * timeSeconds);
            double sine = Math.sin(wd * timeSeconds);
            h = envelope * (c * cosine + d * sine);
            hVelocity = envelope * ((d * wd - gamma * c) * cosine
                    + (-c * wd - gamma * d) * sine);
            hAcceleration = -2 * gamma * hVelocity - w0 * w0 * h;
        } else {
            // Over-damped roots r1/r2 are real and distinct.
            double root = Math.sqrt(gamma * gamma - w0 * w0);
            double r1 = -gamma + root;
            double r2 = -gamma - root;
            double c1 = (velocityDifference - r2 * c) / (r1 - r2);
            double c2 = c - c1;
            double e1 = Math.exp(r1 * timeSeconds);
            double e2 = Math.exp(r2 * timeSeconds);
            h = c1 * e1 + c2 * e2;
            hVelocity = r1 * c1 * e1 + r2 * c2 * e2;
            hAcceleration = -2 * gamma * hVelocity - w0 * w0 * h;
        }
        return new State(xParticular + h, vParticular + hVelocity, aParticular + hAcceleration);
    }

    public record State(double displacement, double velocity, double acceleration) {
    }
}
