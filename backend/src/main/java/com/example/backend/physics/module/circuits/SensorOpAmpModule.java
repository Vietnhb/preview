package com.example.backend.physics.module.circuits;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.Map;
import java.util.Objects;

/** Typed sensor divider followed by a single-supply saturated op-amp comparator. */
public final class SensorOpAmpModule implements PhysicsModule<SensorOpAmpModule.Parameters> {
    public static final String MODULE_ID = "sensor_op_amp";
    public static final String NUMERICAL_SOLVER_ID = "sensor_op_amp_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "sensor_op_amp_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        requireUnit(quantities, "supply_voltage", "V");
        requireUnit(quantities, "sensor_resistance", "ohm");
        requireUnit(quantities, "reference_resistance", "ohm");
        requireUnit(quantities, "op_amp_gain", "1");
        requireUnit(quantities, "threshold_voltage", "V");
        return new Parameters(quantities.require("supply_voltage"), quantities.require("sensor_resistance"),
                quantities.require("reference_resistance"), quantities.require("op_amp_gain"),
                quantities.require("threshold_voltage"));
    }

    @Override
    public SolverOutput solve(Parameters p, SimulationClock clock) {
        Objects.requireNonNull(p, "parameters");
        Objects.requireNonNull(clock, "clock");
        double scale = Math.max(p.sensorResistance(), p.referenceResistance());
        double normalizedSensorResistance = p.sensorResistance() / scale;
        double normalizedReferenceResistance = p.referenceResistance() / scale;
        double normalizedTotal = normalizedSensorResistance + normalizedReferenceResistance;
        double sensorFraction = normalizedSensorResistance / normalizedTotal;
        double sensorVoltage = p.supplyVoltage() * sensorFraction;
        double referenceVoltage = p.supplyVoltage() * (1.0 - sensorFraction);
        double amplifiedOutput = saturate(p.opAmpGain() * (sensorVoltage - referenceVoltage), p.supplyVoltage());
        double ledState = amplifiedOutput >= p.thresholdVoltage() ? 1.0 : 0.0;
        double dividerCurrent = (p.supplyVoltage() / scale) / normalizedTotal;
        double sensorPower = p.supplyVoltage() * dividerCurrent;
        requireFinite(sensorVoltage, referenceVoltage, amplifiedOutput, sensorPower);
        return new SolverOutput(clock.sampleTimes(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("sensorVoltage", sensorVoltage, "referenceVoltage", referenceVoltage,
                        "amplifiedOutput", amplifiedOutput, "ledState", ledState, "sensorPower", sensorPower));
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters p, double timeSeconds) {
        Objects.requireNonNull(p, "parameters");
        requireCheckpoint(timeSeconds);
        // Derive the divider around its Thevenin midpoint, then apply rail comparisons without sharing clamp code.
        double midpoint = 0.5 * p.supplyVoltage();
        double scale = Math.max(p.sensorResistance(), p.referenceResistance());
        double normalizedSensor = p.sensorResistance() / scale;
        double normalizedReference = p.referenceResistance() / scale;
        double normalizedTotal = normalizedSensor + normalizedReference;
        double offset = midpoint * ((normalizedSensor - normalizedReference) / normalizedTotal);
        double sensorVoltage = midpoint + offset;
        double referenceVoltage = midpoint - offset;
        double differentialCommand = p.opAmpGain() * (2.0 * offset);
        double amplifiedOutput = Math.clamp(differentialCommand, 0.0, p.supplyVoltage());
        double ledState = amplifiedOutput < p.thresholdVoltage() ? 0.0 : 1.0;
        double dividerCurrent = (p.supplyVoltage() / scale) / normalizedTotal;
        double sensorPower = p.supplyVoltage() * dividerCurrent;
        requireFinite(sensorVoltage, referenceVoltage, amplifiedOutput, sensorPower);
        return new AnalyticalPoint(Map.of("sensorVoltage", sensorVoltage, "referenceVoltage", referenceVoltage,
                "amplifiedOutput", amplifiedOutput, "ledState", ledState, "sensorPower", sensorPower));
    }

    private static double saturate(double value, double upperRail) {
        if (value <= 0.0) return 0.0;
        if (value >= upperRail) return upperRail;
        return value;
    }

    private static void requireUnit(CanonicalQuantityBag values, String key, String expected) {
        if (!expected.equals(values.unit(key))) throw new IllegalArgumentException("Sensor op-amp " + key + " must use " + expected);
    }

    private static void requireCheckpoint(double time) {
        if (!Double.isFinite(time) || time < 0.0) throw new IllegalArgumentException("Sensor op-amp reference time is invalid");
    }

    private static void requireFinite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) throw new ArithmeticException("Sensor op-amp output is not finite");
    }

    public record Parameters(double supplyVoltage, double sensorResistance, double referenceResistance,
                             double opAmpGain, double thresholdVoltage) {
        public Parameters {
            if (!Double.isFinite(supplyVoltage) || supplyVoltage <= 0.0
                    || !Double.isFinite(sensorResistance) || sensorResistance <= 0.0
                    || !Double.isFinite(referenceResistance) || referenceResistance <= 0.0
                    || !Double.isFinite(opAmpGain) || opAmpGain <= 0.0 || !Double.isFinite(thresholdVoltage)) {
                throw new IllegalArgumentException("Sensor op-amp inputs must be finite and in their physical domain");
            }
        }
    }
}
