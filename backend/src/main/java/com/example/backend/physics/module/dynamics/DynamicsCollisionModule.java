package com.example.backend.physics.module.dynamics;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed one-dimensional perfectly elastic collision for two point masses. */
public final class DynamicsCollisionModule implements PhysicsModule<DynamicsCollisionModule.Parameters> {
    public static final String MODULE_ID = "dynamics_collision";
    public static final String NUMERICAL_SOLVER_ID = "dynamics_collision_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "dynamics_collision_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        requireUnit(quantities, "mass_1", "kg");
        requireUnit(quantities, "mass_2", "kg");
        requireUnit(quantities, "initial_position_1", "m");
        requireUnit(quantities, "initial_position_2", "m");
        requireUnit(quantities, "velocity_1", "m/s");
        requireUnit(quantities, "velocity_2", "m/s");
        return new Parameters(quantities.require("mass_1"), quantities.require("mass_2"),
                quantities.require("initial_position_1"), quantities.require("initial_position_2"),
                quantities.require("velocity_1"), quantities.require("velocity_2"));
    }

    @Override
    public SolverOutput solve(Parameters p, SimulationClock clock) {
        Objects.requireNonNull(p, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        double event = collisionTime(p.initialPosition1(), p.initialPosition2(), p.velocity1(), p.velocity2());
        boolean collides = event > 0.0;
        double after1 = collides ? ((p.mass1() - p.mass2()) * p.velocity1() + 2.0 * p.mass2() * p.velocity2())
                / (p.mass1() + p.mass2()) : p.velocity1();
        double after2 = collides ? ((p.mass2() - p.mass1()) * p.velocity2() + 2.0 * p.mass1() * p.velocity1())
                / (p.mass1() + p.mass2()) : p.velocity2();
        double eventPosition = collides ? 0.5 * ((p.initialPosition1() + p.velocity1() * event)
                + (p.initialPosition2() + p.velocity2() * event)) : 0.0;
        requireFinite(event, after1, after2, eventPosition);
        List<Double> x1 = new ArrayList<>(time.size());
        List<Double> x2 = new ArrayList<>(time.size());
        List<Double> v1 = new ArrayList<>(time.size());
        List<Double> v2 = new ArrayList<>(time.size());
        List<Double> position1 = new ArrayList<>(time.size());
        List<Double> position2 = new ArrayList<>(time.size());
        List<Double> velocity1 = new ArrayList<>(time.size());
        List<Double> velocity2 = new ArrayList<>(time.size());
        for (double t : time) {
            double firstVelocity = collides && t >= event ? after1 : p.velocity1();
            double secondVelocity = collides && t >= event ? after2 : p.velocity2();
            double firstPosition = collides && t > event ? eventPosition + after1 * (t - event)
                    : p.initialPosition1() + p.velocity1() * t;
            double secondPosition = collides && t > event ? eventPosition + after2 * (t - event)
                    : p.initialPosition2() + p.velocity2() * t;
            requireFinite(firstPosition, secondPosition, firstVelocity, secondVelocity);
            x1.add(firstPosition);
            x2.add(secondPosition);
            v1.add(firstVelocity);
            v2.add(secondVelocity);
            position1.add(firstPosition);
            position2.add(secondPosition);
            velocity1.add(firstVelocity);
            velocity2.add(secondVelocity);
        }
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("position1", List.copyOf(position1));
        values.put("position2", List.copyOf(position2));
        values.put("velocity1", List.copyOf(velocity1));
        values.put("velocity2", List.copyOf(velocity2));
        return new SolverOutput(time, Map.of("x1", List.copyOf(x1), "x2", List.copyOf(x2)),
                Map.of("v1", List.copyOf(v1), "v2", List.copyOf(v2)), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters p, double timeSeconds) {
        Objects.requireNonNull(p, "parameters");
        requireCheckpoint(timeSeconds);
        double event = collisionTime(p.initialPosition1(), p.initialPosition2(), p.velocity1(), p.velocity2());
        boolean collides = event > 0.0;
        double after1 = p.velocity1();
        double after2 = p.velocity2();
        double eventPosition = 0.0;
        if (collides) {
            double totalMass = p.mass1() + p.mass2();
            double centerVelocity = (p.mass1() * p.velocity1() + p.mass2() * p.velocity2()) / totalMass;
            double relativeVelocity = p.velocity1() - p.velocity2();
            after1 = centerVelocity - (p.mass2() / totalMass) * relativeVelocity;
            after2 = centerVelocity + (p.mass1() / totalMass) * relativeVelocity;
            double eventPosition1 = p.initialPosition1() + p.velocity1() * event;
            double eventPosition2 = p.initialPosition2() + p.velocity2() * event;
            eventPosition = (eventPosition1 + eventPosition2) / 2.0;
        }
        double firstPosition = collides && timeSeconds > event
                ? eventPosition + after1 * (timeSeconds - event)
                : p.initialPosition1() + p.velocity1() * timeSeconds;
        double secondPosition = collides && timeSeconds > event
                ? eventPosition + after2 * (timeSeconds - event)
                : p.initialPosition2() + p.velocity2() * timeSeconds;
        double firstVelocity = collides && timeSeconds >= event ? after1 : p.velocity1();
        double secondVelocity = collides && timeSeconds >= event ? after2 : p.velocity2();
        requireFinite(firstPosition, secondPosition, firstVelocity, secondVelocity);
        return new AnalyticalPoint(Map.of("position1", firstPosition, "position2", secondPosition,
                "velocity1", firstVelocity, "velocity2", secondVelocity));
    }

    private static double collisionTime(double x1, double x2, double v1, double v2) {
        double gap = x2 - x1;
        double closingSpeed = v1 - v2;
        if (gap > 0.0 && closingSpeed > 0.0) return gap / closingSpeed;
        if (gap < 0.0 && closingSpeed < 0.0) return -gap / -closingSpeed;
        return -1.0;
    }

    private static void requireUnit(CanonicalQuantityBag values, String key, String unit) {
        if (!unit.equals(values.unit(key))) throw new IllegalArgumentException("Collision " + key + " must use " + unit);
    }
    private static void requireCheckpoint(double t) {
        if (!Double.isFinite(t) || t < 0.0) throw new IllegalArgumentException("Collision reference time is invalid");
    }
    private static void requireFinite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) throw new ArithmeticException("Collision output is not finite");
    }

    public record Parameters(double mass1, double mass2, double initialPosition1, double initialPosition2,
                             double velocity1, double velocity2) {
        public Parameters {
            if (!Double.isFinite(mass1) || mass1 <= 0.0 || !Double.isFinite(mass2) || mass2 <= 0.0
                    || !Double.isFinite(initialPosition1) || !Double.isFinite(initialPosition2)
                    || !Double.isFinite(velocity1) || !Double.isFinite(velocity2)) {
                throw new IllegalArgumentException("Collision masses must be positive and all inputs finite");
            }
        }
    }
}
