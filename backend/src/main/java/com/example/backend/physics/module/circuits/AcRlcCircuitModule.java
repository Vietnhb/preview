package com.example.backend.physics.module.circuits;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.Map;
import java.util.Objects;

/** Typed steady-state series AC RLC calculations with a separately formulated admittance oracle. */
public final class AcRlcCircuitModule implements PhysicsModule<AcRlcCircuitModule.Parameters> {
    public static final String MODULE_ID = "ac_rlc_circuit";
    public static final String NUMERICAL_SOLVER_ID = "ac_rlc_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "ac_rlc_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        requireUnit(quantities, "resistance", "ohm");
        requireUnit(quantities, "inductance", "H");
        requireUnit(quantities, "capacitance", "F");
        requireUnit(quantities, "frequency", "Hz");
        requireUnit(quantities, "rms_voltage", "V");
        return new Parameters(quantities.require("resistance"), quantities.require("inductance"),
                quantities.require("capacitance"), quantities.require("frequency"), quantities.require("rms_voltage"));
    }

    @Override
    public SolverOutput solve(Parameters p, SimulationClock clock) {
        Objects.requireNonNull(p, "parameters");
        Objects.requireNonNull(clock, "clock");
        double omega = 2.0 * Math.PI * p.frequency();
        double xL = omega * p.inductance();
        double xC = 1.0 / (omega * p.capacitance());
        double impedance = Math.hypot(p.resistance(), xL - xC);
        double current = p.rmsVoltage() / impedance;
        double powerFactor = p.resistance() / impedance;
        double realPower = current * current * p.resistance();
        double phase = Math.atan2(xL - xC, p.resistance());
        requireFinite(impedance, current, powerFactor, realPower, phase);
        return new SolverOutput(clock.sampleTimes(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("impedance", impedance, "rmsCurrent", current, "powerFactor", powerFactor,
                        "realPower", realPower, "phase", phase));
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters p, double timeSeconds) {
        Objects.requireNonNull(p, "parameters");
        requireCheckpoint(timeSeconds);
        double angularRate = (2.0 * p.frequency()) * Math.PI;
        double netReactance = (angularRate * p.inductance()) - 1.0 / (angularRate * p.capacitance());
        double scale = Math.max(p.resistance(), Math.abs(netReactance));
        double normalizedResistance = p.resistance() / scale;
        double normalizedReactance = netReactance / scale;
        double normalizedImpedance = Math.hypot(normalizedResistance, normalizedReactance);
        double inverseImpedance = (1.0 / scale) / normalizedImpedance;
        double current = p.rmsVoltage() * inverseImpedance;
        double powerFactor = normalizedResistance / normalizedImpedance;
        double realPower = (p.rmsVoltage() * current) * powerFactor;
        double phase = Math.atan2(normalizedReactance, normalizedResistance);
        double impedance = scale * normalizedImpedance;
        requireFinite(impedance, current, powerFactor, realPower, phase);
        return new AnalyticalPoint(Map.of("impedance", impedance, "rmsCurrent", current,
                "powerFactor", powerFactor, "realPower", realPower, "phase", phase));
    }

    private static void requireUnit(CanonicalQuantityBag values, String key, String expected) {
        if (!expected.equals(values.unit(key))) throw new IllegalArgumentException("AC RLC " + key + " must use " + expected);
    }

    private static void requireCheckpoint(double time) {
        if (!Double.isFinite(time) || time < 0.0) throw new IllegalArgumentException("AC RLC reference time is invalid");
    }

    private static void requireFinite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) throw new ArithmeticException("AC RLC output is not finite");
    }

    public record Parameters(double resistance, double inductance, double capacitance,
                             double frequency, double rmsVoltage) {
        public Parameters {
            if (!Double.isFinite(resistance) || resistance <= 0.0
                    || !Double.isFinite(inductance) || inductance <= 0.0
                    || !Double.isFinite(capacitance) || capacitance <= 0.0
                    || !Double.isFinite(frequency) || frequency <= 0.0
                    || !Double.isFinite(rmsVoltage) || rmsVoltage < 0.0) {
                throw new IllegalArgumentException("AC RLC inputs must be finite; R/L/C/f positive and voltage non-negative");
            }
        }
    }
}
