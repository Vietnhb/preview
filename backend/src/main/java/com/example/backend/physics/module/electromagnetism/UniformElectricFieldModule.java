package com.example.backend.physics.module.electromagnetism;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed constant-field motion model between parallel plates. */
public final class UniformElectricFieldModule implements PhysicsModule<UniformElectricFieldModule.Parameters> {
    public static final String MODULE_ID = "uniform_electric_field";
    public static final String NUMERICAL_SOLVER_ID = "uniform_electric_field_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "uniform_electric_field_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        return new Parameters(
                requireCanonical(quantities, "charge", "C"),
                requireCanonical(quantities, "mass", "kg"),
                requireCanonical(quantities, "potential_difference", "V"),
                requireCanonical(quantities, "plate_separation", "m"),
                requireCanonical(quantities, "initial_velocity", "m/s"),
                requireCanonical(quantities, "travel_time", "s"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> times = Objects.requireNonNull(clock, "clock").sampleTimes();

        double fieldStrength = parameters.potentialDifference() / parameters.plateSeparation();
        double electricForce = parameters.charge() * fieldStrength;
        double acceleration = electricForce / parameters.mass();
        double transverseDisplacement = 0.5 * acceleration
                * parameters.travelTime() * parameters.travelTime();
        double longitudinalDisplacement = parameters.initialVelocity() * parameters.travelTime();
        requireFiniteResults(fieldStrength, electricForce, acceleration,
                transverseDisplacement, longitudinalDisplacement);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("fieldStrength", repeated(fieldStrength, times.size()));
        values.put("electricForce", repeated(electricForce, times.size()));
        values.put("acceleration", repeated(acceleration, times.size()));
        values.put("transverseDisplacement", repeated(transverseDisplacement, times.size()));
        values.put("longitudinalDisplacement", repeated(longitudinalDisplacement, times.size()));
        return new SolverOutput(times, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Uniform-field reference time must be finite and non-negative");
        }

        // Re-derive force, acceleration, and displacements through separately
        // factored equations instead of calling the numerical calculation path.
        double fieldStrength = parameters.potentialDifference() / parameters.plateSeparation();
        double electricForce = (parameters.charge() * parameters.potentialDifference())
                / parameters.plateSeparation();
        double acceleration = (parameters.charge() / parameters.mass()) * fieldStrength;
        double timeSquared = parameters.travelTime() * parameters.travelTime();
        double transverseDisplacement = ((parameters.charge() * parameters.potentialDifference())
                / (2.0 * parameters.mass() * parameters.plateSeparation())) * timeSquared;
        double longitudinalDisplacement = parameters.travelTime()
                * parameters.initialVelocity();
        requireFiniteResults(fieldStrength, electricForce, acceleration,
                transverseDisplacement, longitudinalDisplacement);

        Map<String, Double> values = new LinkedHashMap<>();
        values.put("fieldStrength", fieldStrength);
        values.put("electricForce", electricForce);
        values.put("acceleration", acceleration);
        values.put("transverseDisplacement", transverseDisplacement);
        values.put("longitudinalDisplacement", longitudinalDisplacement);
        return new AnalyticalPoint(values);
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String expectedUnit) {
        if (!expectedUnit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical uniform-field quantity " + key
                    + " must use unit " + expectedUnit);
        }
        return quantities.require(key);
    }

    private static List<Double> repeated(double value, int count) {
        return Collections.nCopies(count, value);
    }

    private static void requireFiniteResults(double fieldStrength, double electricForce,
                                             double acceleration, double transverseDisplacement,
                                             double longitudinalDisplacement) {
        double[] values = {fieldStrength, electricForce, acceleration,
                transverseDisplacement, longitudinalDisplacement};
        String[] keys = {"fieldStrength", "electricForce", "acceleration",
                "transverseDisplacement", "longitudinalDisplacement"};
        for (int index = 0; index < values.length; index++) {
            if (!Double.isFinite(values[index])) {
                throw new ArithmeticException("Uniform-field result is not finite: " + keys[index]);
            }
        }
    }

    /** SI-valued inputs canonicalized at the schema boundary. */
    public record Parameters(double charge, double mass, double potentialDifference,
                             double plateSeparation, double initialVelocity, double travelTime) {
        public Parameters {
            if (!Double.isFinite(charge)
                    || !Double.isFinite(mass) || mass <= 0.0
                    || !Double.isFinite(potentialDifference) || potentialDifference < 0.0
                    || !Double.isFinite(plateSeparation) || plateSeparation <= 0.0
                    || !Double.isFinite(initialVelocity) || initialVelocity < 0.0
                    || !Double.isFinite(travelTime) || travelTime < 0.0) {
                throw new IllegalArgumentException("Uniform-field inputs must be finite and in their physical domain");
            }
        }
    }
}
