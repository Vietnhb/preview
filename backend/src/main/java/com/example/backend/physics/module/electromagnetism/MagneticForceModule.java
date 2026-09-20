package com.example.backend.physics.module.electromagnetism;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Magnetic force on a point charge in a fixed coordinate plane.
 *
 * <p>The incoming speed points along +x; the magnetic field lies in the xy
 * plane at the supplied angle from the velocity. The force vector therefore
 * points along z, with its sign set by the charge and the right-hand rule.</p>
 */
public final class MagneticForceModule implements PhysicsModule<MagneticForceModule.Parameters> {
    public static final String MODULE_ID = "magnetic_force";
    public static final String NUMERICAL_SOLVER_ID = "magnetic_force_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "magnetic_force_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        return new Parameters(
                requireCanonical(quantities, "charge", "C"),
                requireCanonical(quantities, "speed", "m/s"),
                requireCanonical(quantities, "magnetic_field", "T"),
                requireCanonical(quantities, "velocity_field_angle", "rad"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> times = Objects.requireNonNull(clock, "clock").sampleTimes();

        double forceX;
        double forceY;
        double forceZ;
        if (isZeroForceCase(parameters)) {
            forceX = 0.0;
            forceY = 0.0;
            forceZ = 0.0;
        } else {
            double velocityX = parameters.speed();
            double velocityY = 0.0;
            double velocityZ = 0.0;
            double fieldX = parameters.magneticField() * Math.cos(parameters.angle());
            double fieldY = parameters.magneticField() * sineForCrossProduct(parameters.angle());
            double fieldZ = 0.0;
            // Evaluate q(v x B) component by component in the declared frame.
            forceX = parameters.charge() * (velocityY * fieldZ - velocityZ * fieldY);
            forceY = parameters.charge() * (velocityZ * fieldX - velocityX * fieldZ);
            forceZ = parameters.charge() * (velocityX * fieldY - velocityY * fieldX);
        }
        double magnitude = Math.hypot(Math.hypot(forceX, forceY), forceZ);
        requireFinite(forceX, forceY, forceZ, magnitude);

        Map<String, Double> scalarOutputs = Map.of(
                "magneticForce", magnitude,
                "magneticForceX", forceX,
                "magneticForceY", forceY,
                "magneticForceZ", forceZ);
        return new SolverOutput(times, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), scalarOutputs);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Magnetic-force reference time must be finite and non-negative");
        }

        // Independent projection formulation: remove the field component
        // parallel to the velocity, then use |q| v |B_perp|.
        double magnitude;
        double forceZ;
        if (isZeroForceCase(parameters)) {
            magnitude = 0.0;
            forceZ = 0.0;
        } else {
            double halfAngle = parameters.angle() / 2.0;
            double perpendicularFraction = 2.0 * Math.sin(halfAngle) * Math.cos(halfAngle);
            double perpendicularField = parameters.magneticField() * perpendicularFraction;
            magnitude = (Math.abs(parameters.charge()) * parameters.speed()) * perpendicularField;
            forceZ = Math.copySign(magnitude, parameters.charge());
        }
        double forceX = 0.0;
        double forceY = 0.0;
        requireFinite(forceX, forceY, forceZ, magnitude);

        Map<String, Double> values = new LinkedHashMap<>();
        values.put("magneticForce", magnitude);
        values.put("magneticForceX", forceX);
        values.put("magneticForceY", forceY);
        values.put("magneticForceZ", forceZ);
        return new AnalyticalPoint(values);
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String expectedUnit) {
        if (!expectedUnit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical magnetic-force quantity " + key
                    + " must use unit " + expectedUnit);
        }
        return quantities.require(key);
    }

    private static double sineForCrossProduct(double angle) {
        if (angle == 0.0 || angle == Math.PI) return 0.0;
        return Math.sin(angle);
    }

    private static boolean isZeroForceCase(Parameters parameters) {
        return parameters.charge() == 0.0 || parameters.speed() == 0.0
                || parameters.magneticField() == 0.0
                || parameters.angle() == 0.0 || parameters.angle() == Math.PI;
    }

    private static void requireFinite(double forceX, double forceY, double forceZ, double magnitude) {
        if (!Double.isFinite(forceX) || !Double.isFinite(forceY)
                || !Double.isFinite(forceZ) || !Double.isFinite(magnitude)) {
            throw new ArithmeticException("Magnetic-force result is not finite");
        }
    }

    /** SI-valued inputs canonicalized at the schema boundary. */
    public record Parameters(double charge, double speed, double magneticField, double angle) {
        public Parameters {
            if (!Double.isFinite(charge)
                    || !Double.isFinite(speed) || speed < 0.0
                    || !Double.isFinite(magneticField) || magneticField < 0.0
                    || !Double.isFinite(angle) || angle < 0.0 || angle > Math.PI) {
                throw new IllegalArgumentException(
                        "Magnetic-force inputs must be finite and in their physical domain");
            }
        }
    }
}
