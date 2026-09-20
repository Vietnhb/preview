package com.example.backend.physics.module.practical;

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

/** Typed bounded straight-line fit sampled across a scalar x interval. */
public final class ExperimentalDataGraphModule
        implements PhysicsModule<ExperimentalDataGraphModule.Parameters> {
    public static final String MODULE_ID = "experimental_data_graph";
    public static final String NUMERICAL_SOLVER_ID = "experimental_data_graph_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "experimental_data_graph_reference_v2";
    private static final int MIN_SAMPLE_COUNT = 2;
    private static final int MAX_SAMPLE_COUNT = 4096;

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        double sampleCount = requireCanonical(quantities, "sample_count", "1");
        if (sampleCount != Math.rint(sampleCount)
                || sampleCount < MIN_SAMPLE_COUNT || sampleCount > MAX_SAMPLE_COUNT) {
            throw new IllegalArgumentException("Experimental graph sample_count must be an integer in [2,4096]");
        }
        return new Parameters(requireCanonical(quantities, "x_start", "1"),
                requireCanonical(quantities, "x_end", "1"),
                requireCanonical(quantities, "slope", "1"),
                requireCanonical(quantities, "intercept", "1"), (int) sampleCount);
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(clock, "clock");
        List<Double> x = new ArrayList<>(parameters.sampleCount());
        List<Double> y = new ArrayList<>(parameters.sampleCount());
        List<Double> time = new ArrayList<>(parameters.sampleCount());
        List<Double> slopes = new ArrayList<>(parameters.sampleCount());
        List<Double> intercepts = new ArrayList<>(parameters.sampleCount());
        double span = parameters.xEnd() - parameters.xStart();
        for (int index = 0; index < parameters.sampleCount(); index++) {
            double coordinate = parameters.xStart() + span * index / (parameters.sampleCount() - 1.0);
            double value = parameters.intercept() + parameters.slope() * coordinate;
            requireFinite("x", coordinate);
            requireFinite("y", value);
            time.add(coordinate);
            x.add(coordinate);
            y.add(value);
            slopes.add(parameters.slope());
            intercepts.add(parameters.intercept());
        }
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("x", List.copyOf(x));
        values.put("y", List.copyOf(y));
        values.put("slope", List.copyOf(slopes));
        values.put("intercept", List.copyOf(intercepts));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double xCoordinate) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(xCoordinate)) {
            throw new IllegalArgumentException("Experimental graph reference coordinate must be finite");
        }
        double x = Math.max(parameters.xStart(), Math.min(parameters.xEnd(), xCoordinate));
        double startY = parameters.intercept() + parameters.slope() * parameters.xStart();
        double endY = parameters.intercept() + parameters.slope() * parameters.xEnd();
        double fractionalPosition = (x - parameters.xStart()) / (parameters.xEnd() - parameters.xStart());
        double y = startY + fractionalPosition * (endY - startY);
        requireFinite("reference x", x);
        requireFinite("reference y", y);
        return new AnalyticalPoint(Map.of("x", x, "y", y,
                "slope", parameters.slope(), "intercept", parameters.intercept()));
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical experimental-graph quantity " + key
                    + " must use unit " + unit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Experimental-graph quantity must be finite: " + key);
        return value;
    }

    private static void requireFinite(String key, double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Experimental-graph output must be finite: " + key);
    }

    /** Scalar fit, bounded x interval and sample resolution. */
    public record Parameters(double xStart, double xEnd, double slope,
                             double intercept, int sampleCount) {
        public Parameters {
            if (!Double.isFinite(xStart) || !Double.isFinite(xEnd) || xEnd <= xStart
                    || !Double.isFinite(xEnd - xStart)
                    || !Double.isFinite(slope) || !Double.isFinite(intercept)
                    || !Double.isFinite(intercept + slope * xStart)
                    || !Double.isFinite(intercept + slope * xEnd)
                    || sampleCount < MIN_SAMPLE_COUNT || sampleCount > MAX_SAMPLE_COUNT) {
                throw new IllegalArgumentException("Experimental graph interval, fit and sample count are outside the valid domain");
            }
        }
    }
}
