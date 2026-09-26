package com.example.backend.physics.module.modern;

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

/** Typed deterministic expectation-value model for radioactive decay. */
public final class RadioactiveDecayModule implements PhysicsModule<RadioactiveDecayModule.Parameters> {
    public static final String MODULE_ID = "radioactive_decay";
    public static final String NUMERICAL_SOLVER_ID = "radioactive_decay_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "radioactive_decay_reference_v2";
    private static final String REMAINING_COUNT = "remainingCount";
    private static final String ACTIVITY = "activity";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        requireUnit(quantities, "initial_count", "1");
        requireUnit(quantities, "decay_constant", "1/s");
        return new Parameters(quantities.require("initial_count"), quantities.require("decay_constant"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(clock, "clock");
        List<Double> time = clock.sampleTimes();
        List<Double> remainingCount = new ArrayList<>(time.size());
        List<Double> activity = new ArrayList<>(time.size());
        for (double currentTime : time) {
            double count = parameters.initialCount()
                    * Math.exp(-parameters.decayConstant() * currentTime);
            double currentActivity = parameters.decayConstant() * count;
            requireFinite(REMAINING_COUNT, count);
            requireFinite(ACTIVITY, currentActivity);
            remainingCount.add(count);
            activity.add(currentActivity);
        }

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put(REMAINING_COUNT, List.copyOf(remainingCount));
        values.put(ACTIVITY, List.copyOf(activity));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Radioactive-decay reference time must be finite and non-negative");
        }

        // Evaluate population in log space, independent of the numerical path's
        // direct product of initial count and exponential factor.
        double remaining = parameters.initialCount() == 0.0 ? 0.0
                : Math.exp(Math.log(parameters.initialCount())
                - parameters.decayConstant() * timeSeconds);
        double activity = parameters.decayConstant() * remaining;
        requireFinite("reference " + REMAINING_COUNT, remaining);
        requireFinite("reference " + ACTIVITY, activity);
        return new AnalyticalPoint(Map.of(REMAINING_COUNT, remaining, ACTIVITY, activity));
    }

    private static void requireUnit(CanonicalQuantityBag quantities, String key, String expectedUnit) {
        if (!expectedUnit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical radioactive-decay quantity " + key
                    + " must use unit " + expectedUnit);
        }
    }

    private static void requireFinite(String outputKey, double value) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Radioactive-decay result is not finite: " + outputKey);
        }
    }

    /** Immutable canonical inputs in nuclei and inverse seconds. */
    public record Parameters(double initialCount, double decayConstant) {
        public Parameters {
            if (!Double.isFinite(initialCount) || initialCount < 0.0
                    || !Double.isFinite(decayConstant) || decayConstant < 0.0) {
                throw new IllegalArgumentException("Radioactive-decay inputs must be finite and non-negative");
            }
        }
    }
}
