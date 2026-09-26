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

/** Typed parallel-resistor solver with an independent conductance-based reference oracle. */
public final class ResistorsParallelModule implements PhysicsModule<ResistorsParallelModule.Parameters> {
    public static final String MODULE_ID = "resistors_parallel";
    public static final String NUMERICAL_SOLVER_ID = "resistors_parallel_solver";
    public static final String REFERENCE_SOLVER_ID = "resistors_parallel_reference";
    private static final String EQUIVALENT_RESISTANCE = "equivalentResistance";
    private static final String BRANCH_CURRENT_1 = "branchCurrent1";
    private static final String BRANCH_CURRENT_2 = "branchCurrent2";
    private static final String TOTAL_CURRENT = "totalCurrent";

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
        double smallerResistance = Math.min(parameters.resistance1, parameters.resistance2);
        double largerResistance = Math.max(parameters.resistance1, parameters.resistance2);
        double equivalentResistance = smallerResistance / (1.0 + smallerResistance / largerResistance);
        requireFinitePositive(equivalentResistance, EQUIVALENT_RESISTANCE);
        double branchCurrent1 = parameters.voltage / parameters.resistance1;
        double branchCurrent2 = parameters.voltage / parameters.resistance2;
        double totalCurrent = parameters.voltage / equivalentResistance;
        requireFinite(branchCurrent1, BRANCH_CURRENT_1);
        requireFinite(branchCurrent2, BRANCH_CURRENT_2);
        requireFinite(totalCurrent, TOTAL_CURRENT);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put(EQUIVALENT_RESISTANCE, repeated(equivalentResistance, time.size()));
        values.put(TOTAL_CURRENT, repeated(totalCurrent, time.size()));
        values.put(BRANCH_CURRENT_1, repeated(branchCurrent1, time.size()));
        values.put(BRANCH_CURRENT_2, repeated(branchCurrent2, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        requireCheckpoint(timeSeconds);
        double conductance1 = 1.0 / parameters.resistance1;
        double conductance2 = 1.0 / parameters.resistance2;
        double totalConductance = conductance1 + conductance2;
        requireFinitePositive(totalConductance, "totalConductance");
        double equivalentResistance = 1.0 / totalConductance;
        double branchCurrent1 = parameters.voltage * conductance1;
        double branchCurrent2 = parameters.voltage * conductance2;
        double totalCurrent = branchCurrent1 + branchCurrent2;
        requireFinitePositive(equivalentResistance, EQUIVALENT_RESISTANCE);
        requireFinite(branchCurrent1, BRANCH_CURRENT_1);
        requireFinite(branchCurrent2, BRANCH_CURRENT_2);
        requireFinite(totalCurrent, TOTAL_CURRENT);
        return new AnalyticalPoint(Map.of(
                EQUIVALENT_RESISTANCE, equivalentResistance,
                TOTAL_CURRENT, totalCurrent,
                BRANCH_CURRENT_1, branchCurrent1,
                BRANCH_CURRENT_2, branchCurrent2));
    }

    private static List<Double> repeated(double value, int count) {
        return java.util.Collections.nCopies(count, value);
    }

    private static void requireCheckpoint(double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
            throw new IllegalArgumentException("Parallel-resistor reference time must be finite and non-negative");
        }
    }

    private static void requireFinite(double value, String quantity) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Parallel-resistor result is not finite: " + quantity);
        }
    }

    private static void requireFinitePositive(double value, String quantity) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new ArithmeticException("Parallel-resistor result is outside the positive finite domain: " + quantity);
        }
    }

    public record Parameters(double voltage, double resistance1, double resistance2) {
        public Parameters {
            if (!Double.isFinite(voltage) || !Double.isFinite(resistance1) || resistance1 <= 0
                    || !Double.isFinite(resistance2) || resistance2 <= 0) {
                throw new IllegalArgumentException("Parallel-resistor voltage must be finite and both resistances positive and finite");
            }
        }
    }
}
