package com.example.backend.physics.module.circuits;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed Ohm's law module with an independently evaluated reference oracle. */
public final class OhmsLawModule implements PhysicsModule<OhmsLawModule.Parameters> {
    public static final String MODULE_ID = "ohms_law";
    public static final String NUMERICAL_SOLVER_ID = "ohms_law_solver";
    public static final String REFERENCE_SOLVER_ID = "ohms_law_reference";

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
        return new Parameters(quantities.require("voltage"), quantities.require("resistance"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        requireInputs(parameters);
        if (clock == null) throw new IllegalArgumentException("Simulation clock is required");

        double voltage = parameters.voltage();
        double resistance = parameters.resistance();
        double current = voltage / resistance;
        double power = voltage * current;
        requireFiniteResult(current, "current");
        requireFiniteResult(power, "power");

        List<Double> time = clock.sampleTimes();
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("voltage", repeated(voltage, time.size()));
        values.put("resistance", repeated(resistance, time.size()));
        values.put("current", repeated(current, time.size()));
        values.put("power", repeated(power, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        requireInputs(parameters);
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
            throw new IllegalArgumentException("Reference time must be finite and non-negative");
        }

        // The oracle recomputes both relationships directly from primitive inputs.
        double oracleVoltage = parameters.voltage();
        double oracleResistance = parameters.resistance();
        double oracleCurrent = oracleVoltage / oracleResistance;
        double oraclePower = (oracleVoltage * oracleVoltage) / oracleResistance;
        requireFiniteResult(oracleCurrent, "current");
        requireFiniteResult(oraclePower, "power");
        return new AnalyticalPoint(Map.of(
                "voltage", oracleVoltage,
                "resistance", oracleResistance,
                "current", oracleCurrent,
                "power", oraclePower));
    }

    private static List<Double> repeated(double value, int count) {
        return java.util.Collections.nCopies(count, value);
    }

    private static void requireInputs(Parameters parameters) {
        if (parameters == null) throw new IllegalArgumentException("Ohm's law parameters are required");
        if (!Double.isFinite(parameters.voltage())
                || !Double.isFinite(parameters.resistance())
                || parameters.resistance() <= 0.0) {
            throw new IllegalArgumentException("Voltage must be finite and resistance must be finite and positive");
        }
    }

    private static void requireFiniteResult(double value, String outputKey) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Ohm's law produced a non-finite " + outputKey);
        }
    }

    /** Primitive state only; equations stay in the numerical and reference paths. */
    public record Parameters(double voltage, double resistance) {
        public Parameters {
            if (!Double.isFinite(voltage) || !Double.isFinite(resistance) || resistance <= 0.0) {
                throw new IllegalArgumentException("Voltage must be finite and resistance must be finite and positive");
            }
        }
    }
}
