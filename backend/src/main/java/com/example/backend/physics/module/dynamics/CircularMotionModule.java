package com.example.backend.physics.module.dynamics;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed uniform circular-motion model with a separately derived kinematic oracle. */
public final class CircularMotionModule implements PhysicsModule<CircularMotionModule.Parameters> {
    public static final String MODULE_ID = "circular_motion";
    public static final String NUMERICAL_SOLVER_ID = "circular_motion_solver";
    public static final String REFERENCE_SOLVER_ID = "circular_motion_reference";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        requireUnit(quantities, "mass", "kg");
        requireUnit(quantities, "radius", "m");
        requireUnit(quantities, "speed", "m/s");
        return new Parameters(
                quantities.require("mass"),
                quantities.require("radius"),
                quantities.require("speed"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        List<Double> time = clock.sampleTimes();
        double angularSpeed = parameters.speed() / parameters.radius();
        double frequency = parameters.speed() / (2.0 * Math.PI * parameters.radius());
        double period = (2.0 * Math.PI) / angularSpeed;
        double centripetalAcceleration = (parameters.speed() * parameters.speed()) / parameters.radius();
        double centripetalForce = parameters.mass() * centripetalAcceleration;
        requireFiniteResults(angularSpeed, period, frequency, centripetalAcceleration, centripetalForce);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("angularSpeed", Collections.nCopies(time.size(), angularSpeed));
        values.put("period", Collections.nCopies(time.size(), period));
        values.put("frequency", Collections.nCopies(time.size(), frequency));
        values.put("centripetalAcceleration", Collections.nCopies(time.size(), centripetalAcceleration));
        values.put("centripetalForce", Collections.nCopies(time.size(), centripetalForce));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        requireCheckpointTime(timeSeconds);

        // The oracle starts with the revolution period, then derives frequency and
        // angular speed. Acceleration and force follow from a_rad = omega^2 r.
        double revolutionPeriod = (2.0 * Math.PI * parameters.radius()) / parameters.speed();
        double cyclesPerSecond = 1.0 / revolutionPeriod;
        double radiansPerSecond = (2.0 * Math.PI) * cyclesPerSecond;
        double radialAcceleration = parameters.radius() * radiansPerSecond * radiansPerSecond;
        double inwardForce = parameters.mass() * radialAcceleration;
        requireFiniteResults(radiansPerSecond, revolutionPeriod, cyclesPerSecond,
                radialAcceleration, inwardForce);

        return new AnalyticalPoint(Map.of(
                "angularSpeed", radiansPerSecond,
                "period", revolutionPeriod,
                "frequency", cyclesPerSecond,
                "centripetalAcceleration", radialAcceleration,
                "centripetalForce", inwardForce));
    }

    private static void requireFiniteResults(double angularSpeed, double period, double frequency,
                                             double acceleration, double force) {
        if (!Double.isFinite(angularSpeed) || !Double.isFinite(period) || !Double.isFinite(frequency)
                || !Double.isFinite(acceleration) || !Double.isFinite(force)) {
            throw new IllegalArgumentException("Circular-motion outputs exceed the finite numeric domain");
        }
    }

    private static void requireCheckpointTime(double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("timeSeconds must be finite and non-negative");
        }
    }

    private static void requireUnit(CanonicalQuantityBag quantities, String key, String expectedUnit) {
        if (!expectedUnit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Circular-motion quantity '" + key
                    + "' must be normalized to " + expectedUnit);
        }
    }

    public record Parameters(double mass, double radius, double speed) {
        public Parameters {
            if (!Double.isFinite(mass) || mass <= 0.0
                    || !Double.isFinite(radius) || radius <= 0.0
                    || !Double.isFinite(speed) || speed <= 0.0) {
                throw new IllegalArgumentException("Circular motion requires finite positive mass, radius and speed");
            }
        }
    }
}
