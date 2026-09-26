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

/** Typed ideal RC discharging model with an independently evaluated reference oracle. */
public final class RcDischargingModule implements PhysicsModule<RcDischargingModule.Parameters> {
    public static final String MODULE_ID = "rc_discharging";
    public static final String NUMERICAL_SOLVER_ID = "rc_discharging_solver";
    public static final String REFERENCE_SOLVER_ID = "rc_discharging_reference";

    private static final String VOLTAGE_UNIT = "V";
    private static final String RESISTANCE_UNIT = "ohm";
    private static final String CAPACITANCE_UNIT = "F";
    private static final String VOLTAGE = "voltage";
    private static final String CURRENT = "current";

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
        requireUnit(quantities, VOLTAGE, VOLTAGE_UNIT);
        requireUnit(quantities, "resistance", RESISTANCE_UNIT);
        requireUnit(quantities, "capacitance", CAPACITANCE_UNIT);
        return new Parameters(quantities.require(VOLTAGE), quantities.require("resistance"),
                quantities.require("capacitance"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        requireInputs(parameters);
        if (clock == null) throw new IllegalArgumentException("Simulation clock is required");

        double initialVoltage = parameters.voltage();
        double resistance = parameters.resistance();
        double timeConstant = parameters.resistance() * parameters.capacitance();
        double initialCurrent = -initialVoltage / resistance;
        requireFinite(initialCurrent, CURRENT);

        List<Double> time = clock.sampleTimes();
        List<Double> capacitorVoltage = new ArrayList<>(time.size());
        List<Double> current = new ArrayList<>(time.size());
        for (double seconds : time) {
            requireValidTime(seconds);
            double decay = Math.exp(-seconds / timeConstant);
            double voltageAtTime = initialVoltage * decay;
            double currentAtTime = initialCurrent * decay;
            requireFinite(voltageAtTime, VOLTAGE);
            requireFinite(currentAtTime, CURRENT);
            capacitorVoltage.add(voltageAtTime);
            current.add(currentAtTime);
        }

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put(VOLTAGE, List.copyOf(capacitorVoltage));
        values.put(CURRENT, List.copyOf(current));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        requireInputs(parameters);
        requireValidTime(timeSeconds);

        double timeConstant = parameters.resistance() * parameters.capacitance();
        double eFoldCount = timeSeconds / timeConstant;
        // The oracle evaluates the exponential through pow and derives current from
        // the resistor voltage at the checkpoint, independently of the solver's
        // precomputed initial-current series.
        double remainingFraction = Math.pow(Math.E, -eFoldCount);
        double oracleVoltage = parameters.voltage() * remainingFraction;
        double oracleCurrent = -oracleVoltage / parameters.resistance();
        requireFinite(oracleVoltage, VOLTAGE);
        requireFinite(oracleCurrent, CURRENT);
        return new AnalyticalPoint(Map.of(VOLTAGE, oracleVoltage, CURRENT, oracleCurrent));
    }

    private static void requireUnit(CanonicalQuantityBag quantities, String key, String expectedUnit) {
        if (!expectedUnit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical " + key + " unit must be " + expectedUnit);
        }
    }

    private static void requireInputs(Parameters parameters) {
        if (parameters == null) throw new IllegalArgumentException("RC discharging parameters are required");
        double timeConstant = parameters.resistance() * parameters.capacitance();
        if (!Double.isFinite(parameters.voltage())
                || !Double.isFinite(parameters.resistance()) || parameters.resistance() <= 0.0
                || !Double.isFinite(parameters.capacitance()) || parameters.capacitance() <= 0.0
                || !Double.isFinite(timeConstant) || timeConstant == 0.0) {
            throw new IllegalArgumentException("Voltage must be finite; resistance and capacitance must be positive and finite with a finite nonzero product");
        }
    }

    private static void requireValidTime(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0.0) {
            throw new IllegalArgumentException("RC discharging time must be finite and non-negative");
        }
    }

    private static void requireFinite(double value, String outputKey) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("RC discharging produced a non-finite " + outputKey);
        }
    }

    /** Primitive input state only; equations stay in the numerical and reference paths. */
    public record Parameters(double voltage, double resistance, double capacitance) {
        public Parameters {
            double timeConstant = resistance * capacitance;
            if (!Double.isFinite(voltage)
                    || !Double.isFinite(resistance) || resistance <= 0.0
                    || !Double.isFinite(capacitance) || capacitance <= 0.0
                    || !Double.isFinite(timeConstant) || timeConstant == 0.0) {
                throw new IllegalArgumentException("Voltage must be finite; resistance and capacitance must be positive and finite with a finite nonzero product");
            }
        }
    }
}
