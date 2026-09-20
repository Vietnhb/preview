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

/** Typed constant-acceleration velocity-time graph. */
public final class VelocityTimeGraphModule implements PhysicsModule<VelocityTimeGraphModule.Parameters> {
    public static final String MODULE_ID = "kinematics_velocity_time_graph";
    public static final String NUMERICAL_SOLVER_ID = "kinematics_velocity_time_graph_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "kinematics_velocity_time_graph_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }
    @Override public Parameters bind(CanonicalQuantityBag q) { return Parameters.bind(q); }

    @Override
    public SolverOutput solve(Parameters p, SimulationClock clock) {
        Objects.requireNonNull(p, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        List<Double> velocity = new ArrayList<>(time.size());
        double v = p.initialVelocity();
        for (int i = 0; i < time.size(); i++) {
            requireFinite(v);
            velocity.add(v);
            if (i + 1 < time.size()) v += p.acceleration() * (time.get(i + 1) - time.get(i));
        }
        return new SolverOutput(time, Map.of(), Map.of("velocity", List.copyOf(velocity)), Map.of(),
                Map.of("vx", List.copyOf(velocity)));
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters p, double timeSeconds) {
        Objects.requireNonNull(p, "parameters");
        requireCheckpoint(timeSeconds);
        double velocity = p.initialVelocity() + p.acceleration() * timeSeconds;
        requireFinite(velocity);
        return new AnalyticalPoint(Map.of("vx", velocity));
    }

    private static void requireCheckpoint(double t) { if (!Double.isFinite(t) || t < 0) throw new IllegalArgumentException("Velocity graph reference time is invalid"); }
    private static void requireFinite(double... values) { for (double value : values) if (!Double.isFinite(value)) throw new ArithmeticException("Velocity graph output is not finite"); }

    public record Parameters(double initialPosition, double initialVelocity, double acceleration) {
        public Parameters {
            if (!Double.isFinite(initialPosition) || !Double.isFinite(initialVelocity) || !Double.isFinite(acceleration))
                throw new IllegalArgumentException("Velocity graph quantities must be finite");
        }
        private static Parameters bind(CanonicalQuantityBag q) {
            Objects.requireNonNull(q, "quantities");
            requireUnit(q, "initial_position", "m");
            requireUnit(q, "initial_velocity", "m/s");
            requireUnit(q, "acceleration", "m/s2");
            return new Parameters(q.require("initial_position"), q.require("initial_velocity"), q.require("acceleration"));
        }
        private static void requireUnit(CanonicalQuantityBag q, String key, String unit) {
            if (!unit.equals(q.unit(key))) throw new IllegalArgumentException("Velocity graph " + key + " must use " + unit);
        }
    }
}
