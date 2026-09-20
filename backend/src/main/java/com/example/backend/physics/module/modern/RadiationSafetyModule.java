package com.example.backend.physics.module.modern;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed point-source inverse-square dose-rate model. */
public final class RadiationSafetyModule implements PhysicsModule<RadiationSafetyModule.Parameters> {
    public static final String MODULE_ID = "radiation_safety";
    public static final String NUMERICAL_SOLVER_ID = "radiation_safety_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "radiation_safety_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        return new Parameters(
                requireCanonical(quantities, "reference_dose_rate", "Gy/s"),
                requireCanonical(quantities, "reference_distance", "m"),
                requireCanonical(quantities, "distance", "m"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> times = Objects.requireNonNull(clock, "clock").sampleTimes();

        double doseRate;
        if (parameters.referenceDoseRate() == 0.0) {
            doseRate = 0.0;
        } else {
            double distanceRatio = parameters.referenceDistance() / parameters.distance();
            doseRate = parameters.referenceDoseRate() * distanceRatio * distanceRatio;
        }
        requireFinite(doseRate, "doseRate");

        return new SolverOutput(times, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("doseRate", doseRate));
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Radiation-safety reference time must be finite and non-negative");
        }

        // Independent log-domain evaluation of the radial inverse-square law.
        // The zero-source branch also avoids log(0) and keeps the result exact.
        double doseRate = parameters.referenceDoseRate() == 0.0
                ? 0.0
                : Math.exp(Math.log(parameters.referenceDoseRate())
                    + 2.0 * (Math.log(parameters.referenceDistance())
                    - Math.log(parameters.distance())));
        requireFinite(doseRate, "reference doseRate");
        return new AnalyticalPoint(Map.of("doseRate", doseRate));
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String expectedUnit) {
        String actualUnit = quantities.unit(key);
        if (!expectedUnit.equals(actualUnit)) {
            throw new IllegalArgumentException("Canonical radiation-safety quantity " + key
                    + " must use unit " + expectedUnit + ", got " + actualUnit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Radiation-safety quantity must be finite: " + key);
        }
        return value;
    }

    private static void requireFinite(double value, String outputKey) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Radiation-safety result is not finite: " + outputKey);
        }
    }

    /** SI-valued inputs after schema-boundary canonicalization. */
    public record Parameters(double referenceDoseRate, double referenceDistance, double distance) {
        public Parameters {
            if (!Double.isFinite(referenceDoseRate) || referenceDoseRate < 0.0
                    || !Double.isFinite(referenceDistance) || referenceDistance <= 0.0
                    || !Double.isFinite(distance) || distance <= 0.0) {
                throw new IllegalArgumentException("Dose rate must be non-negative and both distances positive");
            }
        }
    }
}
