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

/** Typed ideal RC charging model with an independently evaluated reference oracle. */
public final class RcChargingModule implements PhysicsModule<RcChargingModule.Parameters> {
    public static final String MODULE_ID = "rc_charging";
    public static final String NUMERICAL_SOLVER_ID = "rc_charging_solver";
    public static final String REFERENCE_SOLVER_ID = "rc_charging_reference";

    private static final String VOLTAGE_UNIT = "V";
    private static final String RESISTANCE_UNIT = "ohm";
    private static final String CAPACITANCE_UNIT = "F";

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
        requireUnit(quantities, "voltage", VOLTAGE_UNIT);
        requireUnit(quantities, "resistance", RESISTANCE_UNIT);
        requireUnit(quantities, "capacitance", CAPACITANCE_UNIT);
        return new Parameters(quantities.require("voltage"), quantities.require("resistance"),
                quantities.require("capacitance"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        requireInputs(parameters);
        if (clock == null) throw new IllegalArgumentException("Simulation clock is required");

        double voltage = parameters.voltage();
        double resistance = parameters.resistance();
        double timeConstant = resistance * parameters.capacitance();
        double initialCurrent = voltage / resistance;
        requireFinite(initialCurrent, "current");

        List<Double> time = clock.sampleTimes();
        List<Double> capacitorVoltage = new ArrayList<>(time.size());
        List<Double> current = new ArrayList<>(time.size());
        for (double seconds : time) {
            requireValidTime(seconds);
            double decay = Math.exp(-seconds / timeConstant);
            // This path uses 1 - exp(-t/RC); the reference path uses expm1.
            double chargedFraction = 1.0 - decay;
            double voltageAtTime = voltage * chargedFraction;
            double currentAtTime = initialCurrent * decay;
            requireFinite(voltageAtTime, "voltage");
            requireFinite(currentAtTime, "current");
            capacitorVoltage.add(voltageAtTime);
            current.add(currentAtTime);
        }

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("voltage", List.copyOf(capacitorVoltage));
        values.put("current", List.copyOf(current));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        requireInputs(parameters);
        requireValidTime(timeSeconds);

        double voltage = parameters.voltage();
        double resistance = parameters.resistance();
        double timeConstant = resistance * parameters.capacitance();
        double exponent = -timeSeconds / timeConstant;
        double independentVoltage = voltage * -Math.expm1(exponent);
        double independentCurrent = (voltage / resistance) * Math.exp(exponent);
        requireFinite(independentVoltage, "voltage");
        requireFinite(independentCurrent, "current");
        return new AnalyticalPoint(Map.of("voltage", independentVoltage, "current", independentCurrent));
    }

    private static void requireUnit(CanonicalQuantityBag quantities, String key, String expectedUnit) {
        if (!expectedUnit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical " + key + " unit must be " + expectedUnit);
        }
    }

    private static void requireInputs(Parameters parameters) {
        if (parameters == null) throw new IllegalArgumentException("RC charging parameters are required");
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
            throw new IllegalArgumentException("RC charging time must be finite and non-negative");
        }
    }

    private static void requireFinite(double value, String outputKey) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("RC charging produced a non-finite " + outputKey);
        }
    }

    /** Primitive input state only; the charging equations stay in the two runtime paths. */
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
