package com.example.backend.physics.module.dynamics;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed constant-force linear-drag motion model with a separately derived oracle. */
public final class LinearDragMotionModule implements PhysicsModule<LinearDragMotionModule.Parameters> {
    public static final String MODULE_ID = "linear_drag_motion";
    public static final String NUMERICAL_SOLVER_ID = "linear_drag_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "linear_drag_reference_v2";

    private static final List<String> OUTPUT_KEYS = List.of("position", "velocity", "acceleration", "dragForce");

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        return new Parameters(
                requireCanonical(quantities, "mass", "kg"),
                requireCanonical(quantities, "initial_position", "m"),
                requireCanonical(quantities, "initial_velocity", "m/s"),
                requireCanonical(quantities, "constant_force", "N"),
                requireCanonical(quantities, "drag_coefficient", "kg/s"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        double rate = parameters.dragCoefficient() / parameters.mass();
        requireFinitePositiveRate(rate);
        double terminalVelocity = parameters.constantForce() / parameters.dragCoefficient();
        double timeConstant = parameters.mass() / parameters.dragCoefficient();

        Map<String, List<Double>> values = new LinkedHashMap<>();
        for (String key : OUTPUT_KEYS) values.put(key, new java.util.ArrayList<>(time.size()));
        List<Double> position = values.get("position");
        List<Double> velocity = values.get("velocity");
        List<Double> acceleration = values.get("acceleration");
        List<Double> dragForce = values.get("dragForce");

        for (double t : time) {
            double decay = Math.exp(-rate * t);
            double v = terminalVelocity + (parameters.initialVelocity() - terminalVelocity) * decay;
            double x = parameters.initialPosition() + terminalVelocity * t
                    + (parameters.initialVelocity() - terminalVelocity) * timeConstant * (1.0 - decay);
            double a = (parameters.constantForce() - parameters.dragCoefficient() * v) / parameters.mass();
            double drag = -parameters.dragCoefficient() * v;
            requireFiniteResults(t, x, v, a, drag);
            position.add(x);
            velocity.add(v);
            acceleration.add(a);
            dragForce.add(drag);
        }

        return new SolverOutput(time, Map.of("x", position), Map.of("x", velocity),
                Map.of("x", acceleration), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Linear-drag reference time must be finite and non-negative");
        }

        // Integrate the decaying initial acceleration instead of reusing the terminal-velocity
        // position/velocity equations in solve(). This form also has stable small-time factors.
        double rate = parameters.dragCoefficient() / parameters.mass();
        requireFinitePositiveRate(rate);
        double z = rate * timeSeconds;
        if (!Double.isFinite(z)) throw new ArithmeticException("Linear-drag reference time scale is not finite");
        double decay = Math.exp(-z);
        double initialAcceleration = (parameters.constantForce()
                - parameters.dragCoefficient() * parameters.initialVelocity()) / parameters.mass();
        double progress = -Math.expm1(-z);
        double velocityFactor = velocityIntegralFactor(z, progress);
        double positionFactor = positionIntegralFactor(z, progress);

        double velocity = parameters.initialVelocity()
                + initialAcceleration * timeSeconds * velocityFactor;
        double position = parameters.initialPosition() + parameters.initialVelocity() * timeSeconds
                + initialAcceleration * timeSeconds * timeSeconds * positionFactor;
        double acceleration = initialAcceleration * decay;
        double dragForce = parameters.mass() * acceleration - parameters.constantForce();
        requireFiniteResults(timeSeconds, position, velocity, acceleration, dragForce);
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("position", position);
        values.put("velocity", velocity);
        values.put("acceleration", acceleration);
        values.put("dragForce", dragForce);
        return new AnalyticalPoint(values);
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        double value = quantities.require(key);
        String actualUnit = quantities.unit(key);
        if (!unit.equals(actualUnit)) {
            throw new IllegalArgumentException("Linear-drag quantity " + key
                    + " must use canonical unit " + unit + ", got " + actualUnit);
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Linear-drag quantity must be finite: " + key);
        }
        return value;
    }

    private static double velocityIntegralFactor(double z, double progress) {
        if (Math.abs(z) >= 1.0e-5) return progress / z;
        return 1.0 - z / 2.0 + z * z / 6.0 - z * z * z / 24.0 + z * z * z * z / 120.0;
    }

    private static double positionIntegralFactor(double z, double progress) {
        if (Math.abs(z) >= 1.0e-3) return (z - progress) / (z * z);
        return 0.5 - z / 6.0 + z * z / 24.0 - z * z * z / 120.0 + z * z * z * z / 720.0;
    }

    private static void requireFinitePositiveRate(double rate) {
        if (!Double.isFinite(rate) || rate <= 0.0) {
            throw new ArithmeticException("Linear-drag rate is outside the finite numeric range");
        }
    }

    private static void requireFiniteResults(double time, double position, double velocity,
                                             double acceleration, double dragForce) {
        if (!Double.isFinite(time) || !Double.isFinite(position) || !Double.isFinite(velocity)
                || !Double.isFinite(acceleration) || !Double.isFinite(dragForce)) {
            throw new ArithmeticException("Linear-drag output is not finite");
        }
    }

    public record Parameters(double mass, double initialPosition, double initialVelocity,
                             double constantForce, double dragCoefficient) {
        public Parameters {
            if (!Double.isFinite(mass) || mass <= 0.0
                    || !Double.isFinite(initialPosition)
                    || !Double.isFinite(initialVelocity)
                    || !Double.isFinite(constantForce)
                    || !Double.isFinite(dragCoefficient) || dragCoefficient <= 0.0) {
                throw new IllegalArgumentException("Linear-drag inputs must be finite with positive mass and drag coefficient");
            }
        }
    }
}
