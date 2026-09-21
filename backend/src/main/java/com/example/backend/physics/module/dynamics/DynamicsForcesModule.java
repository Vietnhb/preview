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

/** Typed one-dimensional force model with dry friction and a hand-derived piecewise oracle. */
public final class DynamicsForcesModule implements PhysicsModule<DynamicsForcesModule.Parameters> {
    public static final String MODULE_ID = "dynamics_forces";
    public static final String NUMERICAL_SOLVER_ID = "dynamics_forces_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "dynamics_forces_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        requireUnit(quantities, "mass", "kg");
        requireUnit(quantities, "net_force", "N");
        requireUnit(quantities, "friction_coefficient", "1");
        requireUnit(quantities, "initial_position", "m");
        requireUnit(quantities, "initial_velocity", "m/s");
        requireUnit(quantities, "gravitational_acceleration", "m/s2");
        return new Parameters(quantities.require("mass"), quantities.require("net_force"),
                quantities.require("friction_coefficient"), quantities.require("initial_position"),
                quantities.require("initial_velocity"), quantities.require("gravitational_acceleration"));
    }

    @Override
    public SolverOutput solve(Parameters p, SimulationClock clock) {
        Objects.requireNonNull(p, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        double frictionForce = p.frictionCoefficient() * p.mass() * p.gravity();
        requireFinite(frictionForce);
        List<Double> position = new ArrayList<>(time.size());
        List<Double> velocity = new ArrayList<>(time.size());
        List<Double> acceleration = new ArrayList<>(time.size());
        List<Double> effectiveForce = new ArrayList<>(time.size());
        double x = p.initialPosition();
        double v = p.initialVelocity();
        for (int i = 0; i < time.size(); i++) {
            double a = numericalAcceleration(p.appliedForce(), frictionForce, p.mass(), v);
            requireFinite(time.get(i), x, v, a);
            position.add(x);
            velocity.add(v);
            acceleration.add(a);
            double force = p.mass() * a;
            requireFinite(force);
            effectiveForce.add(force);
            if (i + 1 < time.size()) {
                double dt = time.get(i + 1) - time.get(i);
                MotionState next = numericalAdvance(x, v, p.appliedForce(), frictionForce, p.mass(), dt);
                requireFinite(next.position(), next.velocity());
                x = next.position();
                v = next.velocity();
            }
        }
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("x", List.copyOf(position));
        values.put("vx", List.copyOf(velocity));
        values.put("ax", List.copyOf(acceleration));
        values.put("force", List.copyOf(effectiveForce));
        return new SolverOutput(time, Map.of("position", List.copyOf(position)),
                Map.of("velocity", List.copyOf(velocity)), Map.of("acceleration", List.copyOf(acceleration)), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters p, double timeSeconds) {
        Objects.requireNonNull(p, "parameters");
        requireCheckpoint(timeSeconds);
        double frictionForce = p.frictionCoefficient() * p.mass() * p.gravity();
        double initialAcceleration = referenceAcceleration(p.appliedForce(), frictionForce, p.mass(), p.initialVelocity());
        double x;
        double v;
        double a;
        if (p.initialVelocity() != 0.0 && p.initialVelocity() * initialAcceleration < 0.0) {
            double stoppingTime = -p.initialVelocity() / initialAcceleration;
            if (timeSeconds >= stoppingTime) {
                double stopPosition = p.initialPosition()
                        + (p.initialVelocity() + 0.0) * (stoppingTime / 2.0);
                double remaining = timeSeconds - stoppingTime;
                a = referenceAcceleration(p.appliedForce(), frictionForce, p.mass(), 0.0);
                x = stopPosition + 0.5 * a * remaining * remaining;
                v = a * remaining;
            } else {
                a = initialAcceleration;
                double finalVelocity = p.initialVelocity() + a * timeSeconds;
                x = p.initialPosition() + (p.initialVelocity() + finalVelocity) * (timeSeconds / 2.0);
                v = finalVelocity;
            }
        } else {
            a = initialAcceleration;
            double finalVelocity = p.initialVelocity() + a * timeSeconds;
            x = p.initialPosition() + (p.initialVelocity() + finalVelocity) * (timeSeconds / 2.0);
            v = finalVelocity;
        }
        requireFinite(x, v, a);
        double force = p.mass() * a;
        requireFinite(force);
        return new AnalyticalPoint(Map.of("x", x, "vx", v, "ax", a, "force", force));
    }

    private static double numericalAcceleration(double appliedForce, double frictionForce,
                                                double mass, double velocity) {
        if (velocity == 0.0) {
            if (Math.abs(appliedForce) <= frictionForce) return 0.0;
            return Math.copySign((Math.abs(appliedForce) - frictionForce) / mass, appliedForce);
        }
        return (appliedForce - Math.copySign(frictionForce, velocity)) / mass;
    }

    private static MotionState numericalAdvance(double position, double velocity, double appliedForce,
                                                double frictionForce, double mass, double dt) {
        double a = numericalAcceleration(appliedForce, frictionForce, mass, velocity);
        if (velocity != 0.0 && velocity * a < 0.0) {
            double stoppingTime = -velocity / a;
            if (stoppingTime <= dt) {
                double stoppedPosition = position + (velocity + 0.0) * (stoppingTime / 2.0);
                double remaining = dt - stoppingTime;
                double restartAcceleration = numericalAcceleration(appliedForce, frictionForce, mass, 0.0);
                return new MotionState(stoppedPosition + 0.5 * restartAcceleration * remaining * remaining,
                        restartAcceleration * remaining);
            }
        }
        return new MotionState(position + velocity * dt + 0.5 * a * dt * dt, velocity + a * dt);
    }

    /** Reference-only force law. Kept separate from the numerical path so a
     * mutation in the stepper cannot validate itself through the same helper. */
    private static double referenceAcceleration(double appliedForce, double frictionForce,
                                                double mass, double velocity) {
        if (velocity == 0.0) {
            double availableForce = Math.abs(appliedForce);
            if (availableForce <= frictionForce) return 0.0;
            return (appliedForce < 0.0 ? -1.0 : 1.0)
                    * (availableForce - frictionForce) / mass;
        }
        double opposingFriction = velocity < 0.0 ? frictionForce : -frictionForce;
        return (appliedForce + opposingFriction) / mass;
    }

    private static void requireUnit(CanonicalQuantityBag values, String key, String unit) {
        if (!unit.equals(values.unit(key))) throw new IllegalArgumentException("Force model " + key + " must use " + unit);
    }
    private static void requireCheckpoint(double t) {
        if (!Double.isFinite(t) || t < 0.0) throw new IllegalArgumentException("Force reference time is invalid");
    }
    private static void requireFinite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) throw new ArithmeticException("Force-model output is not finite");
    }

    public record Parameters(double mass, double appliedForce, double frictionCoefficient,
                             double initialPosition, double initialVelocity, double gravity) {
        public Parameters {
            if (!Double.isFinite(mass) || mass <= 0.0 || !Double.isFinite(appliedForce)
                    || !Double.isFinite(frictionCoefficient) || frictionCoefficient < 0.0
                    || !Double.isFinite(initialPosition) || !Double.isFinite(initialVelocity)
                    || !Double.isFinite(gravity) || gravity < 0.0) {
                throw new IllegalArgumentException("Force-model inputs must be finite; mass positive and friction/gravity non-negative");
            }
        }
    }

    private record MotionState(double position, double velocity) { }
}
