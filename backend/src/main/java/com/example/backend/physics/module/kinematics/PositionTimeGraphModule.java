package com.example.backend.physics.module.kinematics;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed constant-acceleration position-time graph. */
public final class PositionTimeGraphModule implements PhysicsModule<PositionTimeGraphModule.Parameters> {
    public static final String MODULE_ID = "kinematics_position_time_graph";
    public static final String NUMERICAL_SOLVER_ID = "kinematics_position_time_graph_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "kinematics_position_time_graph_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }
    @Override public Parameters bind(CanonicalQuantityBag q) { return Parameters.bind(q); }

    @Override
    public SolverOutput solve(Parameters p, SimulationClock clock) {
        Objects.requireNonNull(p, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        List<Double> position = new ArrayList<>(time.size());
        double x = p.initialPosition();
        double velocity = p.initialVelocity();
        for (int i = 0; i < time.size(); i++) {
            requireFinite(x, velocity);
            position.add(x);
            if (i + 1 < time.size()) {
                double dt = time.get(i + 1) - time.get(i);
                x += velocity * dt + 0.5 * p.acceleration() * dt * dt;
                velocity += p.acceleration() * dt;
                requireFinite(x, velocity);
            }
        }
        return new SolverOutput(time, Map.of("position", List.copyOf(position)), Map.of(), Map.of(), Map.of("x", List.copyOf(position)));
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters p, double timeSeconds) {
        Objects.requireNonNull(p, "parameters");
        requireCheckpoint(timeSeconds);
        double finalVelocity = p.initialVelocity() + p.acceleration() * timeSeconds;
        double position = p.initialPosition() + (p.initialVelocity() + finalVelocity) * (timeSeconds / 2.0);
        requireFinite(finalVelocity, position);
        return new AnalyticalPoint(Map.of("x", position));
    }

    private static void requireCheckpoint(double t) { if (!Double.isFinite(t) || t < 0) throw new IllegalArgumentException("Position graph reference time is invalid"); }
    private static void requireFinite(double... values) { for (double value : values) if (!Double.isFinite(value)) throw new ArithmeticException("Position graph output is not finite"); }

    public record Parameters(double initialPosition, double initialVelocity, double acceleration) {
        public Parameters {
            if (!Double.isFinite(initialPosition) || !Double.isFinite(initialVelocity) || !Double.isFinite(acceleration))
                throw new IllegalArgumentException("Position graph quantities must be finite");
        }
        private static Parameters bind(CanonicalQuantityBag q) {
            Objects.requireNonNull(q, "quantities");
            requireUnit(q, "initial_position", "m");
            requireUnit(q, "initial_velocity", "m/s");
            requireUnit(q, "acceleration", "m/s2");
            return new Parameters(q.require("initial_position"), q.require("initial_velocity"), q.require("acceleration"));
        }
        private static void requireUnit(CanonicalQuantityBag q, String key, String unit) {
            if (!unit.equals(q.unit(key))) throw new IllegalArgumentException("Position graph " + key + " must use " + unit);
        }
    }
}
