package com.example.backend.physics.module.thermal;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed solver and independently expressed ideal-gas reference for a reversible adiabatic process. */
public final class AdiabaticGasModule implements PhysicsModule<AdiabaticGasModule.Parameters> {
    public static final String MODULE_ID = "adiabatic_gas";
    public static final String NUMERICAL_SOLVER_ID = "adiabatic_gas_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "adiabatic_gas_reference_v2";
    private static final String FINAL_PRESSURE = "finalPressure";
    private static final String FINAL_TEMPERATURE = "finalTemperature";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        requireUnit(quantities, "initial_pressure", "Pa");
        requireUnit(quantities, "initial_volume", "m3");
        requireUnit(quantities, "final_volume", "m3");
        requireUnit(quantities, "initial_temperature", "K");
        requireUnit(quantities, "heat_capacity_ratio", "1");
        return new Parameters(
                quantities.require("initial_pressure"),
                quantities.require("initial_volume"),
                quantities.require("final_volume"),
                quantities.require("initial_temperature"),
                quantities.require("heat_capacity_ratio"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        List<Double> time = clock.sampleTimes();
        double volumeRatio = parameters.initialVolume() / parameters.finalVolume();
        double pressureRatio = Math.pow(volumeRatio, parameters.heatCapacityRatio());
        double temperatureRatio = Math.pow(volumeRatio, parameters.heatCapacityRatio() - 1.0);
        double finalPressure = parameters.initialPressure() * pressureRatio;
        double finalTemperature = parameters.initialTemperature() * temperatureRatio;
        double workByGas = (parameters.initialPressure() * parameters.initialVolume()
                - finalPressure * parameters.finalVolume()) / (parameters.heatCapacityRatio() - 1.0);
        requirePositiveFinite(FINAL_PRESSURE, finalPressure);
        requirePositiveFinite(FINAL_TEMPERATURE, finalTemperature);
        requireFinite("work", workByGas);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("initialPressure", Collections.nCopies(time.size(), parameters.initialPressure()));
        values.put(FINAL_PRESSURE, Collections.nCopies(time.size(), finalPressure));
        values.put(FINAL_TEMPERATURE, Collections.nCopies(time.size(), finalTemperature));
        values.put("work", Collections.nCopies(time.size(), workByGas));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("timeSeconds must be finite and non-negative");
        }

        // Derive temperature from the logarithmic Poisson relation. Recover final
        // pressure from the ideal-gas ratio, then use ΔU = n Cv ΔT for the work.
        // This path does not reuse the numerical solver's direct pressure-power or
        // pressure-volume work equations.
        double volumeRatio = parameters.initialVolume() / parameters.finalVolume();
        double logTemperatureRatio = (parameters.heatCapacityRatio() - 1.0) * Math.log(volumeRatio);
        double temperatureRatio = Math.exp(logTemperatureRatio);
        double finalTemperature = parameters.initialTemperature() * temperatureRatio;
        double finalPressure = parameters.initialPressure() * temperatureRatio * volumeRatio;
        double initialMolarEnergyScale = parameters.initialPressure() * parameters.initialVolume()
                / (parameters.heatCapacityRatio() - 1.0);
        double workByGas = initialMolarEnergyScale * (1.0 - temperatureRatio);
        requirePositiveFinite(FINAL_TEMPERATURE, finalTemperature);
        requirePositiveFinite(FINAL_PRESSURE, finalPressure);
        requireFinite("work", workByGas);

        return new AnalyticalPoint(Map.of(
                "initialPressure", parameters.initialPressure(),
                FINAL_PRESSURE, finalPressure,
                FINAL_TEMPERATURE, finalTemperature,
                "work", workByGas));
    }

    private static void requireUnit(CanonicalQuantityBag quantities, String key, String expectedUnit) {
        if (!expectedUnit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Adiabatic gas quantity '" + key
                    + "' must be normalized to " + expectedUnit);
        }
    }

    private static void requireFinite(String outputKey, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Adiabatic gas produced non-finite output: " + outputKey);
        }
    }

    private static void requirePositiveFinite(String outputKey, double value) {
        requireFinite(outputKey, value);
        if (value <= 0.0) {
            throw new IllegalArgumentException("Adiabatic gas produced non-positive output: " + outputKey);
        }
    }

    public record Parameters(double initialPressure, double initialVolume, double finalVolume,
                             double initialTemperature, double heatCapacityRatio) {
        public Parameters {
            if (!Double.isFinite(initialPressure) || initialPressure <= 0.0
                    || !Double.isFinite(initialVolume) || initialVolume <= 0.0
                    || !Double.isFinite(finalVolume) || finalVolume <= 0.0
                    || !Double.isFinite(initialTemperature) || initialTemperature <= 0.0
                    || !Double.isFinite(heatCapacityRatio) || heatCapacityRatio <= 1.0) {
                throw new IllegalArgumentException("Adiabatic gas requires positive finite pressure, volumes, temperature, and heat capacity ratio greater than one");
            }
        }
    }
}
