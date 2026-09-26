package com.example.backend.physics.module.circuits;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.PhysicalConstants;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed Shockley diode module with a separately evaluated reference equation. */
public final class DiodeCharacteristicModule implements PhysicsModule<DiodeCharacteristicModule.Parameters> {
    public static final String MODULE_ID = "diode_characteristic";
    public static final String NUMERICAL_SOLVER_ID = "diode_characteristic_solver";
    public static final String REFERENCE_SOLVER_ID = "diode_characteristic_reference";

    private static final double LOG_MAX_VALUE = Math.log(Double.MAX_VALUE);
    private static final String THERMAL_VOLTAGE = "thermalVoltage";
    private static final String CURRENT = "current";
    private static final String POWER = "power";
    private static final String REFERENCE_PREFIX = "reference ";

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
        requireUnit(quantities, "voltage", "V");
        requireUnit(quantities, "saturation_current", "A");
        requireUnit(quantities, "ideality_factor", "1");
        requireUnit(quantities, "temperature", "K");
        return new Parameters(quantities.require("voltage"), quantities.require("saturation_current"),
                quantities.require("ideality_factor"), quantities.require("temperature"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        double thermalVoltage = PhysicalConstants.BOLTZMANN * parameters.temperature
                / PhysicalConstants.ELEMENTARY_CHARGE;
        requirePositiveFinite(thermalVoltage, THERMAL_VOLTAGE);
        double characteristicVoltage = parameters.idealityFactor * thermalVoltage;
        requirePositiveFinite(characteristicVoltage, "characteristicVoltage");
        double exponent = parameters.voltage / characteristicVoltage;
        requireFinite(exponent, "dimensionless voltage");
        double current = numericalCurrent(parameters.saturationCurrent, exponent);
        double power = parameters.voltage * current;
        requireFinite(current, CURRENT);
        requireFinite(power, POWER);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put(THERMAL_VOLTAGE, repeated(thermalVoltage, time.size()));
        values.put(CURRENT, repeated(current, time.size()));
        values.put(POWER, repeated(power, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        requireCheckpoint(timeSeconds);

        // Use q/k and a different grouping from the numerical kT/q and V/(n Vt) path.
        double thermalVoltage = (PhysicalConstants.BOLTZMANN / PhysicalConstants.ELEMENTARY_CHARGE)
                * parameters.temperature;
        requirePositiveFinite(thermalVoltage, REFERENCE_PREFIX + THERMAL_VOLTAGE);
        double exponent = ((parameters.voltage / parameters.idealityFactor) / parameters.temperature)
                * (PhysicalConstants.ELEMENTARY_CHARGE / PhysicalConstants.BOLTZMANN);
        requireFinite(exponent, "reference dimensionless voltage");
        double current = referenceCurrent(parameters.saturationCurrent, exponent);
        double power = parameters.voltage * current;
        requireFinite(current, REFERENCE_PREFIX + CURRENT);
        requireFinite(power, REFERENCE_PREFIX + POWER);
        return new AnalyticalPoint(Map.of(
                THERMAL_VOLTAGE, thermalVoltage,
                CURRENT, current,
                POWER, power));
    }

    private static double numericalCurrent(double saturationCurrent, double exponent) {
        double current;
        if (exponent <= 700.0) {
            current = saturationCurrent * Math.expm1(exponent);
        } else {
            double logCurrentMagnitude = Math.log(saturationCurrent) + exponent;
            if (!Double.isFinite(logCurrentMagnitude) || logCurrentMagnitude > LOG_MAX_VALUE) {
                throw new ArithmeticException("Diode current exceeds the finite numeric range");
            }
            current = Math.exp(logCurrentMagnitude) - saturationCurrent;
        }
        requireFinite(current, "current");
        return current;
    }

    private static double referenceCurrent(double saturationCurrent, double exponent) {
        double exponentialDifference;
        if (Math.abs(exponent) < 1.0e-3) {
            // Independent Taylor evaluation avoids cancellation in exp(x) - 1 near zero.
            exponentialDifference = exponent * (1.0 + exponent * (0.5 + exponent * (1.0 / 6.0
                    + exponent * (1.0 / 24.0 + exponent / 120.0))));
            exponentialDifference *= saturationCurrent;
        } else if (exponent > 50.0) {
            double logCurrentMagnitude = Math.log(saturationCurrent) + exponent;
            if (!Double.isFinite(logCurrentMagnitude) || logCurrentMagnitude > LOG_MAX_VALUE) {
                throw new ArithmeticException("Diode reference current exceeds the finite numeric range");
            }
            exponentialDifference = Math.exp(logCurrentMagnitude) - saturationCurrent;
        } else {
            exponentialDifference = saturationCurrent * (Math.exp(exponent) - 1.0);
        }
        requireFinite(exponentialDifference, "reference current");
        return exponentialDifference;
    }

    private static List<Double> repeated(double value, int count) {
        return java.util.Collections.nCopies(count, value);
    }

    private static void requireUnit(CanonicalQuantityBag quantities, String key, String expected) {
        if (!expected.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical unit for " + key + " must be " + expected);
        }
    }

    private static void requireCheckpoint(double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Diode reference time must be finite and non-negative");
        }
    }

    private static void requireFinite(double value, String quantity) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Diode result is not finite: " + quantity);
        }
    }

    private static void requirePositiveFinite(double value, String quantity) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new ArithmeticException("Diode result is outside the positive finite domain: " + quantity);
        }
    }

    public record Parameters(double voltage, double saturationCurrent, double idealityFactor, double temperature) {
        public Parameters {
            if (!Double.isFinite(voltage) || !Double.isFinite(saturationCurrent) || saturationCurrent <= 0.0
                    || !Double.isFinite(idealityFactor) || idealityFactor <= 0.0
                    || !Double.isFinite(temperature) || temperature <= 0.0) {
                throw new IllegalArgumentException(
                        "Diode voltage must be finite; saturation current, ideality factor, and temperature must be finite and positive");
            }
        }
    }
}
