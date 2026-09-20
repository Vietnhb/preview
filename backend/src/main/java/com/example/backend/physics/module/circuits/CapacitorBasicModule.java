package com.example.backend.physics.module.circuits;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed ideal-capacitor module with an independently evaluated reference oracle. */
public final class CapacitorBasicModule implements PhysicsModule<CapacitorBasicModule.Parameters> {
    public static final String MODULE_ID = "capacitor_basic";
    public static final String NUMERICAL_SOLVER_ID = "capacitor_solver";
    public static final String REFERENCE_SOLVER_ID = "capacitor_reference";

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
        if (quantities == null) throw new IllegalArgumentException("Canonical quantities are required");
        return new Parameters(quantities.require("capacitance"), quantities.require("voltage"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        requireInputs(parameters);
        if (clock == null) throw new IllegalArgumentException("Simulation clock is required");

        double capacitance = parameters.capacitance();
        double voltage = parameters.voltage();
        double charge = capacitance * voltage;
        double energy = 0.5 * capacitance * voltage * voltage;
        requireFiniteResult(charge, "charge");
        requireFiniteResult(energy, "energy");

        List<Double> time = clock.sampleTimes();
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("charge", repeated(charge, time.size()));
        values.put("energy", repeated(energy, time.size()));
        values.put("voltage", repeated(voltage, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        requireInputs(parameters);
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
            throw new IllegalArgumentException("Reference time must be finite and non-negative");
        }

        // Re-evaluate Q = C V and U = Q V / 2 from the primitive input values.
        double oracleCapacitance = parameters.capacitance();
        double oracleVoltage = parameters.voltage();
        double oracleCharge = oracleCapacitance * oracleVoltage;
        double oracleEnergy = 0.5 * (oracleCapacitance * oracleVoltage) * oracleVoltage;
        requireFiniteResult(oracleCharge, "charge");
        requireFiniteResult(oracleEnergy, "energy");
        return new AnalyticalPoint(Map.of(
                "charge", oracleCharge,
                "energy", oracleEnergy,
                "voltage", oracleVoltage));
    }

    private static List<Double> repeated(double value, int count) {
        return java.util.Collections.nCopies(count, value);
    }

    private static void requireInputs(Parameters parameters) {
        if (parameters == null) throw new IllegalArgumentException("Capacitor parameters are required");
        if (!Double.isFinite(parameters.capacitance()) || parameters.capacitance() <= 0.0
                || !Double.isFinite(parameters.voltage())) {
            throw new IllegalArgumentException("Capacitance must be finite and positive and voltage must be finite");
        }
    }

    private static void requireFiniteResult(double value, String outputKey) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Capacitor model produced a non-finite " + outputKey);
        }
    }

    /** Primitive state only; equations stay in the numerical and reference paths. */
    public record Parameters(double capacitance, double voltage) {
        public Parameters {
            if (!Double.isFinite(capacitance) || capacitance <= 0.0 || !Double.isFinite(voltage)) {
                throw new IllegalArgumentException("Capacitance must be finite and positive and voltage must be finite");
            }
        }
    }
}
