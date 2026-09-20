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

/** Typed one-dimensional constant-acceleration model with a separate closed-form oracle. */
public final class UniformAccelerationModule implements PhysicsModule<UniformAccelerationModule.Parameters> {
    public static final String MODULE_ID = "uniform_acceleration";
    public static final String NUMERICAL_SOLVER_ID = "uniform_acceleration_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "uniform_acceleration_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        return new Parameters(
                requireCanonical(quantities, "initial_position", "m"),
                requireCanonical(quantities, "initial_velocity", "m/s"),
                requireCanonical(quantities, "acceleration", "m/s2"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        List<Double> x = new ArrayList<>(time.size());
        List<Double> displacement = new ArrayList<>(time.size());
        List<Double> zeroY = new ArrayList<>(time.size());
        List<Double> vx = new ArrayList<>(time.size());
        List<Double> zeroVy = new ArrayList<>(time.size());
        List<Double> ax = new ArrayList<>(time.size());
        List<Double> zeroAy = new ArrayList<>(time.size());

        double position = parameters.initialPosition();
        double velocity = parameters.initialVelocity();
        for (int index = 0; index < time.size(); index++) {
            double currentTime = time.get(index);
            double currentDisplacement = position - parameters.initialPosition();
            requireFinite(currentTime, position, currentDisplacement, velocity, parameters.acceleration());
            x.add(position);
            displacement.add(currentDisplacement);
            zeroY.add(0.0);
            vx.add(velocity);
            zeroVy.add(0.0);
            ax.add(parameters.acceleration());
            zeroAy.add(0.0);

            if (index + 1 < time.size()) {
                double dt = time.get(index + 1) - currentTime;
                double nextPosition = position + velocity * dt;
                nextPosition += 0.5 * parameters.acceleration() * dt * dt;
                double nextVelocity = velocity + parameters.acceleration() * dt;
                requireFinite(dt, nextPosition, nextVelocity);
                position = nextPosition;
                velocity = nextVelocity;
            }
        }

        Map<String, List<Double>> positions = Map.of("x", x, "y", zeroY);
        Map<String, List<Double>> velocities = Map.of("x", vx, "y", zeroVy);
        Map<String, List<Double>> accelerations = Map.of("x", ax, "y", zeroAy);
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("x", x);
        values.put("displacement", displacement);
        values.put("y", zeroY);
        values.put("vx", vx);
        values.put("vy", zeroVy);
        values.put("ax", ax);
        values.put("ay", zeroAy);
        return new SolverOutput(time, positions, velocities, accelerations, values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Uniform-acceleration reference time must be finite and non-negative");
        }

        // Derive position from average endpoint velocity, independently of the
        // solver's interval-by-interval state integration.
        double finalVelocity = parameters.initialVelocity() + parameters.acceleration() * timeSeconds;
        double displacement = (parameters.initialVelocity() + finalVelocity) * (timeSeconds / 2.0);
        double position = parameters.initialPosition() + displacement;
        requireFinite(timeSeconds, finalVelocity, displacement, position, parameters.acceleration());

        Map<String, Double> values = new LinkedHashMap<>();
        values.put("x", position);
        values.put("displacement", displacement);
        values.put("y", 0.0);
        values.put("vx", finalVelocity);
        values.put("vy", 0.0);
        values.put("ax", parameters.acceleration());
        values.put("ay", 0.0);
        return new AnalyticalPoint(values);
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String expectedUnit) {
        double value = quantities.require(key);
        String actualUnit = quantities.unit(key);
        if (!expectedUnit.equals(actualUnit)) {
            throw new IllegalArgumentException("Uniform-acceleration quantity " + key
                    + " must use canonical unit " + expectedUnit + ", got " + actualUnit);
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Uniform-acceleration quantity must be finite: " + key);
        }
        return value;
    }

    private static void requireFinite(double... values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new ArithmeticException("Uniform-acceleration result exceeds the finite numeric domain");
            }
        }
    }

    public record Parameters(double initialPosition, double initialVelocity, double acceleration) {
        public Parameters {
            if (!Double.isFinite(initialPosition) || !Double.isFinite(initialVelocity)
                    || !Double.isFinite(acceleration)) {
                throw new IllegalArgumentException("Uniform-acceleration inputs must be finite");
            }
        }
    }
}
