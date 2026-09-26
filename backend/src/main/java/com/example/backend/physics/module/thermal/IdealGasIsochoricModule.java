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

/** Typed owner of the constant-volume ideal-gas process. */
public final class IdealGasIsochoricModule implements PhysicsModule<IdealGasIsochoricModule.Parameters> {
    public static final String MODULE_ID = "ideal_gas_isochoric";
    public static final String NUMERICAL_SOLVER_ID = "ideal_gas_isochoric_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "ideal_gas_isochoric_reference_v2";
    private static final String INITIAL_PRESSURE = "initialPressure";
    private static final String INITIAL_VOLUME = "initialVolume";
    private static final String INITIAL_TEMPERATURE = "initialTemperature";
    private static final String FINAL_TEMPERATURE = "finalTemperature";
    private static final String FINAL_PRESSURE = "finalPressure";
    private static final String FINAL_VOLUME = "finalVolume";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        if (quantities == null) {
            throw new IllegalArgumentException("Canonical ideal-gas quantities are required");
        }
        requireUnit(quantities, "initial_pressure", "Pa");
        requireUnit(quantities, "initial_volume", "m3");
        requireUnit(quantities, "initial_temperature", "K");
        requireUnit(quantities, "final_temperature", "K");
        return new Parameters(
                quantities.require("initial_pressure"),
                quantities.require("initial_volume"),
                quantities.require("initial_temperature"),
                quantities.require("final_temperature"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        requireParameters(parameters);
        if (clock == null) {
            throw new IllegalArgumentException("Ideal-gas simulation clock is required");
        }

        double temperatureRatio = parameters.finalTemperature() / parameters.initialTemperature();
        double finalPressure = parameters.initialPressure() * temperatureRatio;
        double finalVolume = parameters.initialVolume();
        double work = 0.0;
        requirePositiveFinite(FINAL_PRESSURE, finalPressure);
        requirePositiveFinite(FINAL_VOLUME, finalVolume);
        requireFinite("work", work);

        List<Double> time = clock.sampleTimes();
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put(INITIAL_PRESSURE, repeated(parameters.initialPressure(), time.size()));
        values.put(INITIAL_VOLUME, repeated(parameters.initialVolume(), time.size()));
        values.put(INITIAL_TEMPERATURE, repeated(parameters.initialTemperature(), time.size()));
        values.put(FINAL_TEMPERATURE, repeated(parameters.finalTemperature(), time.size()));
        values.put(FINAL_PRESSURE, repeated(finalPressure, time.size()));
        values.put(FINAL_VOLUME, repeated(finalVolume, time.size()));
        values.put("work", repeated(work, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        requireParameters(parameters);
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Reference time must be finite and non-negative");
        }

        // Use the logarithmic form of the ideal-gas pressure ratio so the
        // reference path is independent from the numerical solver's direct ratio.
        double logFinalPressure = Math.log(parameters.initialPressure())
                + Math.log(parameters.finalTemperature())
                - Math.log(parameters.initialTemperature());
        double finalPressure = Math.exp(logFinalPressure);
        double finalVolume = parameters.initialVolume();
        double work = 0.0;
        requirePositiveFinite("reference " + FINAL_PRESSURE, finalPressure);
        requirePositiveFinite("reference " + FINAL_VOLUME, finalVolume);
        requireFinite("reference work", work);

        Map<String, Double> values = new LinkedHashMap<>();
        values.put(INITIAL_PRESSURE, parameters.initialPressure());
        values.put(INITIAL_VOLUME, parameters.initialVolume());
        values.put(INITIAL_TEMPERATURE, parameters.initialTemperature());
        values.put(FINAL_TEMPERATURE, parameters.finalTemperature());
        values.put(FINAL_PRESSURE, finalPressure);
        values.put(FINAL_VOLUME, finalVolume);
        values.put("work", work);
        return new AnalyticalPoint(values);
    }

    private static List<Double> repeated(double value, int count) {
        return Collections.nCopies(count, value);
    }

    private static void requireUnit(CanonicalQuantityBag quantities, String key, String expectedUnit) {
        if (!expectedUnit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical unit for " + key + " must be " + expectedUnit);
        }
    }

    private static void requireParameters(Parameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Ideal-gas parameters are required");
        }
    }

    private static void requireFinite(String key, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Isochoric ideal-gas process produced non-finite " + key);
        }
    }

    private static void requirePositiveFinite(String key, double value) {
        requireFinite(key, value);
        if (value <= 0.0) {
            throw new IllegalArgumentException("Isochoric ideal-gas process produced non-positive " + key);
        }
    }

    /** Immutable primitive state bound once from canonical quantities. */
    public record Parameters(double initialPressure, double initialVolume,
                             double initialTemperature, double finalTemperature) {
        public Parameters {
            requirePositiveFinite(INITIAL_PRESSURE, initialPressure);
            requirePositiveFinite(INITIAL_VOLUME, initialVolume);
            requirePositiveFinite(INITIAL_TEMPERATURE, initialTemperature);
            requirePositiveFinite(FINAL_TEMPERATURE, finalTemperature);
        }
    }
}
