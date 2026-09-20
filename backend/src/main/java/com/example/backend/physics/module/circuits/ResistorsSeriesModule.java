package com.example.backend.physics.module.circuits;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed series-resistor solver with an independently expressed voltage-divider oracle. */
public final class ResistorsSeriesModule implements PhysicsModule<ResistorsSeriesModule.Parameters> {
    public static final String MODULE_ID = "resistors_series";
    public static final String NUMERICAL_SOLVER_ID = "resistors_series_solver";
    public static final String REFERENCE_SOLVER_ID = "resistors_series_reference";

    @Override
    public String moduleId() {
        return MODULE_ID;
    }

    @Override
    public String numericalSolverId() {
        return NUMERICAL_SOLVER_ID;
    }

    @Override
    public String referenceSolverId() {
        return REFERENCE_SOLVER_ID;
    }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        return new Parameters(quantities.require("voltage"), quantities.require("resistance_1"),
                quantities.require("resistance_2"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        double equivalentResistance = parameters.resistance1 + parameters.resistance2;
        requireFinitePositive(equivalentResistance, "equivalentResistance");
        double current = parameters.voltage / equivalentResistance;
        requireFinite(current, "current");

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("equivalentResistance", repeated(equivalentResistance, time.size()));
        values.put("totalCurrent", repeated(current, time.size()));
        values.put("branchCurrent1", repeated(current, time.size()));
        values.put("branchCurrent2", repeated(current, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        requireCheckpoint(timeSeconds);
        double totalResistance = parameters.resistance1 + parameters.resistance2;
        requireFinitePositive(totalResistance, "equivalentResistance");

        // Scale by the larger resistor to avoid overflowing the sum in the current ratio.
        double largerResistance = Math.max(parameters.resistance1, parameters.resistance2);
        double smallerResistance = Math.min(parameters.resistance1, parameters.resistance2);
        double totalCurrent = (parameters.voltage / largerResistance)
                / (1.0 + smallerResistance / largerResistance);
        requireFinite(totalCurrent, "totalCurrent");
        return new AnalyticalPoint(Map.of(
                "equivalentResistance", totalResistance,
                "totalCurrent", totalCurrent,
                "branchCurrent1", totalCurrent,
                "branchCurrent2", totalCurrent));
    }

    private static List<Double> repeated(double value, int count) {
        return java.util.Collections.nCopies(count, value);
    }

    private static void requireCheckpoint(double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
            throw new IllegalArgumentException("Series-resistor reference time must be finite and non-negative");
        }
    }

    private static void requireFinite(double value, String quantity) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Series-resistor result is not finite: " + quantity);
        }
    }

    private static void requireFinitePositive(double value, String quantity) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new ArithmeticException("Series-resistor result is outside the positive finite domain: " + quantity);
        }
    }

    public record Parameters(double voltage, double resistance1, double resistance2) {
        public Parameters {
            if (!Double.isFinite(voltage) || !Double.isFinite(resistance1) || resistance1 <= 0
                    || !Double.isFinite(resistance2) || resistance2 <= 0) {
                throw new IllegalArgumentException("Series-resistor voltage must be finite and both resistances positive and finite");
            }
        }
    }
}
