package com.example.backend.physics.module.optics;

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

/** Typed two-source interference model with a separately expressed analytic oracle. */
public final class LightInterferenceModule implements PhysicsModule<LightInterferenceModule.Parameters> {
    public static final String MODULE_ID = "light_interference";
    public static final String NUMERICAL_SOLVER_ID = "light_interference_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "light_interference_reference_v2";
    private static final String PHASE_DIFFERENCE = "phaseDifference";
    private static final String INTENSITY = "intensity";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "canonical quantities");
        requireUnit(quantities, "wavelength", "m");
        requireUnit(quantities, "path_difference", "m");
        requireUnit(quantities, "path_difference_rate", "m/s");
        requireUnit(quantities, "reference_intensity", "W/m2");
        return new Parameters(quantities.require("wavelength"), quantities.require("path_difference"),
                quantities.require("path_difference_rate"), quantities.require("reference_intensity"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> times = Objects.requireNonNull(clock, "clock").sampleTimes();
        Map<String, List<Double>> values = new LinkedHashMap<>();
        List<Double> phaseDifferences = new ArrayList<>(times.size());
        List<Double> intensities = new ArrayList<>(times.size());
        for (double time : times) {
            double pathDifference = parameters.pathDifference()
                    + parameters.pathDifferenceRate() * time;
            requireFinite(pathDifference, "pathDifference");
            double phaseDifference = 2.0 * Math.PI * (pathDifference / parameters.wavelength());
            double halfPhaseCosine = Math.cos(phaseDifference / 2.0);
            double intensity = parameters.referenceIntensity() * halfPhaseCosine * halfPhaseCosine;
            requireFinite(phaseDifference, PHASE_DIFFERENCE);
            requireFinite(intensity, INTENSITY);
            phaseDifferences.add(phaseDifference);
            intensities.add(intensity);
        }
        values.put(PHASE_DIFFERENCE, List.copyOf(phaseDifferences));
        values.put(INTENSITY, List.copyOf(intensities));
        return new SolverOutput(times, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Light-interference reference time must be finite and non-negative");
        }
        double pathDifference = Math.fma(parameters.pathDifferenceRate(), timeSeconds,
                parameters.pathDifference());
        requireFinite(pathDifference, "reference pathDifference");
        double phaseDifference = (pathDifference / parameters.wavelength()) * (2.0 * Math.PI);
        // The oracle uses the sum-of-intensities identity instead of the solver's
        // squared half-phase cosine expression.
        double intensity = 0.5 * parameters.referenceIntensity()
                * (1.0 + Math.cos(phaseDifference));
        requireFinite(phaseDifference, "reference phaseDifference");
        requireFinite(intensity, "reference intensity");
        return new AnalyticalPoint(Map.of(PHASE_DIFFERENCE, phaseDifference, INTENSITY, intensity));
    }

    private static void requireUnit(CanonicalQuantityBag quantities, String key, String expectedUnit) {
        if (!expectedUnit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical unit for " + key + " must be " + expectedUnit);
        }
    }

    private static void requireFinite(double value, String output) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Light-interference result must be finite: " + output);
        }
    }

    /** SI-valued inputs after schema-boundary alias and unit normalization. */
    public record Parameters(double wavelength, double pathDifference, double pathDifferenceRate,
                             double referenceIntensity) {
        public Parameters {
            if (!Double.isFinite(wavelength) || wavelength <= 0.0
                    || !Double.isFinite(pathDifference)
                    || !Double.isFinite(pathDifferenceRate)
                    || !Double.isFinite(referenceIntensity) || referenceIntensity < 0.0) {
                throw new IllegalArgumentException(
                        "Wavelength must be finite and positive, path difference and its rate finite, and reference intensity finite and non-negative");
            }
            if (!Double.isFinite(pathDifference / wavelength)
                    || !Double.isFinite(pathDifferenceRate / wavelength)) {
                throw new IllegalArgumentException("Path difference and its rate divided by wavelength must be finite");
            }
        }
    }
}
