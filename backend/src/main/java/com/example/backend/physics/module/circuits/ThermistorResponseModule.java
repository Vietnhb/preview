package com.example.backend.physics.module.circuits;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.Map;
import java.util.Objects;

/** Typed beta-model NTC thermistor and divider response. */
public final class ThermistorResponseModule implements PhysicsModule<ThermistorResponseModule.Parameters> {
    public static final String MODULE_ID = "thermistor_response";
    public static final String NUMERICAL_SOLVER_ID = "thermistor_response_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "thermistor_response_reference_v2";
    private static final double KELVIN_OFFSET = 273.15;

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        requireUnit(quantities, "reference_resistance", "ohm");
        requireUnit(quantities, "reference_temperature", "K");
        requireUnit(quantities, "beta_constant", "K");
        requireUnit(quantities, "temperature", "degC");
        requireUnit(quantities, "supply_voltage", "V");
        requireUnit(quantities, "divider_resistance", "ohm");
        return new Parameters(quantities.require("reference_resistance"),
                quantities.require("reference_temperature"), quantities.require("beta_constant"),
                quantities.require("temperature"), quantities.require("supply_voltage"),
                quantities.require("divider_resistance"));
    }

    @Override
    public SolverOutput solve(Parameters p, SimulationClock clock) {
        Objects.requireNonNull(p, "parameters");
        Objects.requireNonNull(clock, "clock");
        double temperatureK = p.temperatureCelsius() + KELVIN_OFFSET;
        double betaExponent = p.betaConstant() * (1.0 / temperatureK - 1.0 / p.referenceTemperatureK());
        double resistance = p.referenceResistance() * Math.exp(betaExponent);
        double totalResistance = p.dividerResistance() + resistance;
        requireFinite(totalResistance);
        double dividerVoltage = p.supplyVoltage() * (p.dividerResistance() / totalResistance);
        double current = p.supplyVoltage() / totalResistance;
        double sensorPower = current * current * resistance;
        requireFinite(resistance, dividerVoltage, sensorPower);
        return new SolverOutput(clock.sampleTimes(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("resistance", resistance, "dividerVoltage", dividerVoltage, "sensorPower", sensorPower));
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters p, double timeSeconds) {
        Objects.requireNonNull(p, "parameters");
        requireCheckpoint(timeSeconds);
        double temperatureK = p.temperatureCelsius() + KELVIN_OFFSET;
        // Arrhenius ratio form, evaluated independently from the numerical reciprocal-temperature difference.
        double exponent = (p.betaConstant() / temperatureK)
                * ((p.referenceTemperatureK() - temperatureK) / p.referenceTemperatureK());
        double resistance = p.referenceResistance() * Math.exp(exponent);
        double resistanceRatio = resistance / p.dividerResistance();
        requireFinite(resistanceRatio);
        double dividerVoltage = p.supplyVoltage() / (1.0 + resistanceRatio);
        double current = dividerVoltage / p.dividerResistance();
        double sensorPower = (current * resistance) * current;
        requireFinite(resistance, dividerVoltage, sensorPower);
        return new AnalyticalPoint(Map.of("resistance", resistance,
                "dividerVoltage", dividerVoltage, "sensorPower", sensorPower));
    }

    private static void requireUnit(CanonicalQuantityBag values, String key, String expected) {
        if (!expected.equals(values.unit(key))) {
            throw new IllegalArgumentException("Thermistor canonical quantity " + key + " must use " + expected);
        }
    }

    private static void requireCheckpoint(double time) {
        if (!Double.isFinite(time) || time < 0.0) throw new IllegalArgumentException("Thermistor reference time is invalid");
    }

    private static void requireFinite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) throw new ArithmeticException("Thermistor output is not finite");
    }

    public record Parameters(double referenceResistance, double referenceTemperatureK, double betaConstant,
                             double temperatureCelsius, double supplyVoltage, double dividerResistance) {
        public Parameters {
            if (!Double.isFinite(referenceResistance) || referenceResistance <= 0.0
                    || !Double.isFinite(referenceTemperatureK) || referenceTemperatureK <= 0.0
                    || !Double.isFinite(betaConstant) || betaConstant <= 0.0
                    || !Double.isFinite(temperatureCelsius) || temperatureCelsius <= -KELVIN_OFFSET
                    || !Double.isFinite(supplyVoltage) || supplyVoltage <= 0.0
                    || !Double.isFinite(dividerResistance) || dividerResistance <= 0.0) {
                throw new IllegalArgumentException("Thermistor inputs must be finite and within the physical domain");
            }
        }
    }
}
