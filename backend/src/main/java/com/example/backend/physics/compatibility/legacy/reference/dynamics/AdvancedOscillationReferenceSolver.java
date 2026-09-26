package com.example.backend.physics.compatibility.legacy.reference.dynamics;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.dynamics.DampedForcedOscillationParameters;
import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Independent closed-form checkpoint oracle for damped/forced oscillation. */
@Component
public class AdvancedOscillationReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "advanced_oscillation_reference"; }

    @Override
    public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"damped_forced_oscillation".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported advanced oscillation model: "
                    + PhysicsValues.model(specification));
        }
        DampedForcedOscillationParameters p = DampedForcedOscillationParameters.from(PhysicsValues.bag(specification, overrides));
        ReferenceState state = independentStateAt(p, timeSeconds);
        double energy = 0.5 * p.mass() * state.velocity
                * state.velocity + 0.5 * p.springConstant() * state.displacement * state.displacement;
        return new AnalyticalPoint(Map.of(
                "displacement", state.displacement,
                "velocity", state.velocity,
                "acceleration", state.acceleration,
                "mechanicalEnergy", energy,
                "drivingForce", p.drivingAmplitude() * Math.cos(p.drivingFrequency() * timeSeconds)));
    }

    /**
     * Independent oracle implementation. It deliberately does not call the
     * production stateAt()/amplitude/phase methods so a mutation in the
     * numerical path cannot validate itself.
     */
    private ReferenceState independentStateAt(DampedForcedOscillationParameters p, double t) {
        if (!Double.isFinite(t) || t < 0) throw new IllegalArgumentException("Time must be finite and non-negative");
        double m = p.mass();
        double k = p.springConstant();
        double c = p.dampingCoefficient();
        double f0 = p.drivingAmplitude();
        double w = p.drivingFrequency();
        double w0 = Math.sqrt(k / m);
        double gamma = c / (2 * m);
        double denominator = Math.sqrt(Math.pow(w0 * w0 - w * w, 2) + Math.pow(2 * gamma * w, 2));
        if (denominator == 0 && f0 != 0) {
            throw new IllegalArgumentException("Undamped resonant steady-state amplitude is unbounded");
        }
        double amplitude = denominator == 0 ? 0 : (f0 / m) / denominator;
        double phase = Math.atan2(2 * gamma * w, w0 * w0 - w * w);
        double xParticular = amplitude * Math.cos(w * t - phase);
        double vParticular = -amplitude * w * Math.sin(w * t - phase);
        double aParticular = -amplitude * w * w * Math.cos(w * t - phase);
        double c0 = p.initialDisplacement() - amplitude * Math.cos(phase);
        double velocityDifference = p.initialVelocity() - amplitude * w * Math.sin(phase);
        double h;
        double hVelocity;
        double hAcceleration;
        double tolerance = 1e-12 * Math.max(1, w0);
        if (Math.abs(gamma - w0) <= tolerance) {
            double envelope = Math.exp(-gamma * t);
            double d = velocityDifference + gamma * c0;
            h = envelope * (c0 + d * t);
            hVelocity = envelope * (d - gamma * (c0 + d * t));
            hAcceleration = -2 * gamma * hVelocity - w0 * w0 * h;
        } else if (gamma < w0) {
            double wd = Math.sqrt(w0 * w0 - gamma * gamma);
            double d = (velocityDifference + gamma * c0) / wd;
            double envelope = Math.exp(-gamma * t);
            double cosine = Math.cos(wd * t);
            double sine = Math.sin(wd * t);
            h = envelope * (c0 * cosine + d * sine);
            hVelocity = envelope * ((d * wd - gamma * c0) * cosine
                    + (-c0 * wd - gamma * d) * sine);
            hAcceleration = -2 * gamma * hVelocity - w0 * w0 * h;
        } else {
            double root = Math.sqrt(gamma * gamma - w0 * w0);
            double r1 = -gamma + root;
            double r2 = -gamma - root;
            double c1 = (velocityDifference - r2 * c0) / (r1 - r2);
            double c2 = c0 - c1;
            double e1 = Math.exp(r1 * t);
            double e2 = Math.exp(r2 * t);
            h = c1 * e1 + c2 * e2;
            hVelocity = r1 * c1 * e1 + r2 * c2 * e2;
            hAcceleration = -2 * gamma * hVelocity - w0 * w0 * h;
        }
        return new ReferenceState(xParticular + h, vParticular + hVelocity, aParticular + hAcceleration);
    }

    private record ReferenceState(double displacement, double velocity, double acceleration) { }
}
