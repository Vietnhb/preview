package com.example.backend.physics.module.circuits;

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

/** Typed ideal-transformer module with an independently expressed reference calculation. */
public final class IdealTransformerModule implements PhysicsModule<IdealTransformerModule.Parameters> {
    public static final String MODULE_ID = "ideal_transformer";
    public static final String NUMERICAL_SOLVER_ID = "transformer_solver";
    public static final String REFERENCE_SOLVER_ID = "transformer_reference";

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
        double primaryTurns = quantities.require("primary_turns");
        double secondaryTurns = quantities.require("secondary_turns");
        double primaryVoltage = quantities.require("primary_voltage");
        double secondaryCurrent = quantities.require("secondary_current");
        if (!(primaryTurns > 0) || !(secondaryTurns > 0)
                || primaryTurns != Math.rint(primaryTurns) || secondaryTurns != Math.rint(secondaryTurns)) {
            throw new IllegalArgumentException("Transformer primary_turns and secondary_turns must be positive integers");
        }
        if (!Double.isFinite(primaryVoltage) || primaryVoltage < 0
                || !Double.isFinite(secondaryCurrent) || secondaryCurrent < 0) {
            throw new IllegalArgumentException("Transformer voltage and current magnitudes must be finite and non-negative");
        }
        return new Parameters(primaryTurns, secondaryTurns, primaryVoltage, secondaryCurrent);
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        double turnsRatio = parameters.secondaryTurns / parameters.primaryTurns;
        double secondaryVoltage = parameters.primaryVoltage * turnsRatio;
        double primaryCurrent = parameters.secondaryCurrent * turnsRatio;
        requireFinite(turnsRatio, "turnsRatio");
        requireFinite(secondaryVoltage, "secondaryVoltage");
        requireFinite(primaryCurrent, "primaryCurrent");
        verifyPowerBalance(parameters.primaryVoltage, primaryCurrent,
                secondaryVoltage, parameters.secondaryCurrent);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("turnsRatio", repeated(turnsRatio, time.size()));
        values.put("secondaryVoltage", repeated(secondaryVoltage, time.size()));
        values.put("primaryCurrent", repeated(primaryCurrent, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        requireCheckpoint(timeSeconds);
        // Express the oracle through the primary-to-secondary turns ratio, separately
        // from the numerical path's secondary-to-primary ratio multiplication.
        double primaryToSecondaryRatio = parameters.primaryTurns / parameters.secondaryTurns;
        double secondaryVoltage = parameters.primaryVoltage / primaryToSecondaryRatio;
        double primaryCurrent = parameters.secondaryCurrent / primaryToSecondaryRatio;
        double turnsRatio = 1.0 / primaryToSecondaryRatio;
        requireFinite(turnsRatio, "turnsRatio");
        requireFinite(secondaryVoltage, "secondaryVoltage");
        requireFinite(primaryCurrent, "primaryCurrent");
        verifyPowerBalance(parameters.primaryVoltage, primaryCurrent,
                secondaryVoltage, parameters.secondaryCurrent);
        return new AnalyticalPoint(Map.of(
                "turnsRatio", turnsRatio,
                "secondaryVoltage", secondaryVoltage,
                "primaryCurrent", primaryCurrent));
    }

    private static List<Double> repeated(double value, int count) {
        List<Double> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(value);
        return List.copyOf(result);
    }

    private static void requireCheckpoint(double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
            throw new IllegalArgumentException("Transformer reference checkpoint must be finite and non-negative");
        }
    }

    private static void requireFinite(double value, String quantity) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Transformer output is not finite: " + quantity);
    }

    private static void verifyPowerBalance(double primaryVoltage, double primaryCurrent,
                                           double secondaryVoltage, double secondaryCurrent) {
        double primaryPower = primaryVoltage * primaryCurrent;
        double secondaryPower = secondaryVoltage * secondaryCurrent;
        if (!Double.isFinite(primaryPower) || !Double.isFinite(secondaryPower)) {
            throw new IllegalArgumentException("Transformer power is outside the finite numeric domain");
        }
        double scale = Math.max(Math.abs(primaryPower), Math.abs(secondaryPower));
        double tolerance = Math.max(1.0e-12 * Math.max(1.0, scale), 32.0 * Math.ulp(scale));
        if (Math.abs(primaryPower - secondaryPower) > tolerance) {
            throw new IllegalStateException("Ideal transformer power-balance invariant failed");
        }
    }

    public record Parameters(double primaryTurns, double secondaryTurns,
                             double primaryVoltage, double secondaryCurrent) {
    }
}
