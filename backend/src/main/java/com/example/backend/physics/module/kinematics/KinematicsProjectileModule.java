package com.example.backend.physics.module.kinematics;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;
import com.example.backend.physics.output.PhysicsOutput;
import com.example.backend.physics.output.PhysicsOutputContract;
import com.example.backend.physics.output.PhysicsOutputFrame;
import com.example.backend.physics.output.TimeSeriesOutput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed two-dimensional projectile motion with a closed-form independent reference path. */
public final class KinematicsProjectileModule implements PhysicsModule<KinematicsProjectileModule.Parameters> {
    public static final String MODULE_ID = "kinematics_projectile";
    public static final String NUMERICAL_SOLVER_ID = "kinematics_projectile_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "kinematics_projectile_reference_v2";

    private static final double MAX_DURATION_SECONDS = 3_600.0;
    private static final double MIN_STEP_SECONDS = 1.0e-6;
    private static final int MAX_TIME_INTERVALS = 16_384;
    private static final double MAX_GRAVITATIONAL_ACCELERATION = 30.0;

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public boolean nativeTypedOutput() {
        return true;
    }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        return new Parameters(
                requireCanonical(quantities, "initial_position", "m"),
                requireCanonical(quantities, "initial_height", "m"),
                requireCanonical(quantities, "initial_velocity", "m/s"),
                requireCanonical(quantities, "launch_angle", "rad"),
                requireCanonical(quantities, "gravitational_acceleration", "m/s2"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Samples samples = sample(parameters, clock);
        Map<String, List<Double>> positions = Map.of("x", samples.x(), "y", samples.y());
        Map<String, List<Double>> velocities = Map.of("x", samples.vx(), "y", samples.vy());
        Map<String, List<Double>> accelerations = Map.of("x", samples.ax(), "y", samples.ay());
        Map<String, List<Double>> values = values(samples);
        return new SolverOutput(samples.time(), positions, velocities, accelerations, values);
    }

    @Override
    public PhysicsOutputFrame solveTyped(Parameters parameters, SimulationClock clock,
                                         PhysicsOutputContract contract) {
        Samples samples = sample(parameters, clock);
        List<PhysicsOutput> outputs = new ArrayList<>();
        for (Map.Entry<String, List<Double>> entry : values(samples).entrySet()) {
            outputs.add(new TimeSeriesOutput(entry.getKey(), requiredUnit(contract, entry.getKey()),
                    samples.time(), entry.getValue()));
        }
        return new PhysicsOutputFrame(samples.time(), outputs);
    }

    private static Map<String, List<Double>> values(Samples samples) {
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("x", samples.x());
        values.put("displacement", samples.displacement());
        values.put("y", samples.y());
        values.put("vx", samples.vx());
        values.put("vy", samples.vy());
        values.put("ax", samples.ax());
        values.put("ay", samples.ay());
        return values;
    }

    private static String requiredUnit(PhysicsOutputContract contract, String key) {
        if (contract == null || contract.outputs().get(key) == null) {
            throw new IllegalArgumentException("Typed output contract is missing " + key);
        }
        return contract.outputs().get(key).unit();
    }

    private static Samples sample(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(clock, "clock");
        validateClock(clock);

        double horizontalVelocity = parameters.initialVelocity() * Math.cos(parameters.launchAngle());
        double verticalVelocity = parameters.initialVelocity() * Math.sin(parameters.launchAngle());
        requireFinite(horizontalVelocity, "initial horizontal velocity");
        requireFinite(verticalVelocity, "initial vertical velocity");

        List<Double> time = clock.sampleTimes();
        if (time.size() > MAX_TIME_INTERVALS + 1) {
            throw new IllegalArgumentException("Projectile simulation exceeds the time-sample resource limit");
        }
        List<Double> x = new ArrayList<>(time.size());
        List<Double> y = new ArrayList<>(time.size());
        List<Double> displacement = new ArrayList<>(time.size());
        List<Double> vx = new ArrayList<>(time.size());
        List<Double> vy = new ArrayList<>(time.size());
        List<Double> ax = new ArrayList<>(time.size());
        List<Double> ay = new ArrayList<>(time.size());

        double currentX = parameters.initialPosition();
        double currentY = parameters.initialHeight();
        double currentVerticalVelocity = verticalVelocity;
        for (int index = 0; index < time.size(); index++) {
            double currentTime = time.get(index);
            double currentDisplacement = currentX - parameters.initialPosition();
            requireFinite(currentTime, "sample time");
            requireFinite(currentX, "horizontal position");
            requireFinite(currentY, "vertical position");
            requireFinite(currentDisplacement, "horizontal displacement");
            requireFinite(currentVerticalVelocity, "vertical velocity");

            x.add(currentX);
            y.add(currentY);
            displacement.add(currentDisplacement);
            vx.add(horizontalVelocity);
            vy.add(currentVerticalVelocity);
            ax.add(0.0);
            ay.add(-parameters.gravity());

            if (index + 1 < time.size()) {
                double dt = time.get(index + 1) - currentTime;
                if (!Double.isFinite(dt) || dt <= 0.0) {
                    throw new IllegalArgumentException("Projectile sample times must increase strictly");
                }
                double nextX = currentX + horizontalVelocity * dt;
                double nextY = currentY + currentVerticalVelocity * dt
                        - 0.5 * parameters.gravity() * dt * dt;
                double nextVerticalVelocity = currentVerticalVelocity - parameters.gravity() * dt;
                requireFinite(nextX, "integrated horizontal position");
                requireFinite(nextY, "integrated vertical position");
                requireFinite(nextVerticalVelocity, "integrated vertical velocity");
                currentX = nextX;
                currentY = nextY;
                currentVerticalVelocity = nextVerticalVelocity;
            }
        }
        return new Samples(time, x, y, displacement, vx, vy, ax, ay);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0 || timeSeconds > MAX_DURATION_SECONDS) {
            throw new IllegalArgumentException(
                    "Projectile reference time must be finite, non-negative and within the resource horizon");
        }

        // The oracle derives displacement from average endpoint velocity. The numerical
        // path advances its state one interval at a time, so a shared update helper cannot
        // make a numerical integration defect validate itself.
        double initialVx = parameters.initialVelocity() * Math.cos(parameters.launchAngle());
        double initialVy = parameters.initialVelocity() * Math.sin(parameters.launchAngle());
        double finalVy = initialVy - parameters.gravity() * timeSeconds;
        double horizontalDisplacement = initialVx * timeSeconds;
        double verticalDisplacement = (initialVy + finalVy) * (timeSeconds / 2.0);
        double finalX = parameters.initialPosition() + horizontalDisplacement;
        double finalY = parameters.initialHeight() + verticalDisplacement;
        requireFinite(initialVx, "reference horizontal velocity");
        requireFinite(initialVy, "reference initial vertical velocity");
        requireFinite(finalVy, "reference final vertical velocity");
        requireFinite(horizontalDisplacement, "reference horizontal displacement");
        requireFinite(verticalDisplacement, "reference vertical displacement");
        requireFinite(finalX, "reference horizontal position");
        requireFinite(finalY, "reference vertical position");

        return new AnalyticalPoint(Map.of(
                "x", finalX,
                "displacement", horizontalDisplacement,
                "y", finalY,
                "vx", initialVx,
                "vy", finalVy,
                "ax", 0.0,
                "ay", -parameters.gravity()));
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String expectedUnit) {
        double value = quantities.require(key);
        String actualUnit = quantities.unit(key);
        if (!expectedUnit.equals(actualUnit)) {
            throw new IllegalArgumentException("Projectile quantity " + key
                    + " must use canonical unit " + expectedUnit + ", got " + actualUnit);
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Projectile quantity must be finite: " + key);
        }
        return value;
    }

    private static void validateClock(SimulationClock clock) {
        if (clock.durationSeconds() > MAX_DURATION_SECONDS) {
            throw new IllegalArgumentException("Projectile simulation duration exceeds 3600 seconds");
        }
        if (clock.stepSeconds() < MIN_STEP_SECONDS) {
            throw new IllegalArgumentException("Projectile simulation step must be at least 1 microsecond");
        }
        double intervals = Math.ceil(clock.durationSeconds() / clock.stepSeconds());
        if (!Double.isFinite(intervals) || intervals > MAX_TIME_INTERVALS) {
            throw new IllegalArgumentException("Projectile simulation exceeds 16384 time intervals");
        }
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Projectile " + label + " exceeds the finite numeric domain");
        }
    }

    private static void requireFinite(double... values) {
        for (double value : values) requireFinite(value, "result");
    }

    private record Samples(List<Double> time, List<Double> x, List<Double> y,
                           List<Double> displacement, List<Double> vx, List<Double> vy,
                           List<Double> ax, List<Double> ay) { }

    /** SI-valued projectile inputs after schema defaults and unit normalization. */
    public record Parameters(double initialPosition, double initialHeight, double initialVelocity,
                             double launchAngle, double gravity) {
        public Parameters {
            if (!Double.isFinite(initialPosition) || !Double.isFinite(initialHeight)
                    || !Double.isFinite(initialVelocity) || initialVelocity <= 0.0
                    || !Double.isFinite(launchAngle) || launchAngle < 0.0 || launchAngle > Math.PI
                    || !Double.isFinite(gravity) || gravity < 0.0
                    || gravity > MAX_GRAVITATIONAL_ACCELERATION) {
                throw new IllegalArgumentException(
                        "Projectile inputs must be finite and within the physical domain");
            }
        }
    }
}
