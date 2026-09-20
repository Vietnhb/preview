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

/** Typed ideal two-body calorimetry module with an independently formed energy-balance oracle. */
public final class CalorimetryMixingModule implements PhysicsModule<CalorimetryMixingModule.Parameters> {
    public static final String MODULE_ID = "calorimetry_mixing";
    public static final String NUMERICAL_SOLVER_ID = "calorimetry_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "calorimetry_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        if (quantities == null) {
            throw new IllegalArgumentException("Canonical calorimetry quantities are required");
        }
        requireUnit(quantities, "mass_1", "kg");
        requireUnit(quantities, "specific_heat_1", "J/(kg*K)");
        requireUnit(quantities, "initial_temperature_1", "K");
        requireUnit(quantities, "mass_2", "kg");
        requireUnit(quantities, "specific_heat_2", "J/(kg*K)");
        requireUnit(quantities, "initial_temperature_2", "K");
        return new Parameters(
                quantities.require("mass_1"),
                quantities.require("specific_heat_1"),
                quantities.require("initial_temperature_1"),
                quantities.require("mass_2"),
                quantities.require("specific_heat_2"),
                quantities.require("initial_temperature_2"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        requireParameters(parameters);
        if (clock == null) {
            throw new IllegalArgumentException("Calorimetry simulation clock is required");
        }

        // Compute the equilibrium as a weighted displacement from body 1.
        // The fractional heat capacity is formed by ratio to avoid overflowing
        // the sum of otherwise finite heat capacities.
        double heatCapacity1 = parameters.mass1() * parameters.specificHeat1();
        double heatCapacity2 = parameters.mass2() * parameters.specificHeat2();
        requirePositiveFinite("heatCapacity1", heatCapacity1);
        requirePositiveFinite("heatCapacity2", heatCapacity2);
        double fractionFromBody2 = heatCapacity1 >= heatCapacity2
                ? (heatCapacity2 / heatCapacity1) / (1.0 + heatCapacity2 / heatCapacity1)
                : 1.0 / (1.0 + heatCapacity1 / heatCapacity2);
        double equilibrium = parameters.initialTemperature1()
                + (parameters.initialTemperature2() - parameters.initialTemperature1()) * fractionFromBody2;
        double heat1 = heatCapacity1 * (equilibrium - parameters.initialTemperature1());
        double heat2 = heatCapacity2 * (equilibrium - parameters.initialTemperature2());
        requirePositiveFinite("equilibriumTemperature", equilibrium);
        requireFinite("heat1", heat1);
        requireFinite("heat2", heat2);

        List<Double> time = clock.sampleTimes();
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("temperature1", repeated(equilibrium, time.size()));
        values.put("temperature2", repeated(equilibrium, time.size()));
        values.put("equilibriumTemperature", repeated(equilibrium, time.size()));
        values.put("heat1", repeated(heat1, time.size()));
        values.put("heat2", repeated(heat2, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        requireParameters(parameters);
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Reference time must be finite and non-negative");
        }

        // Independent oracle: conserve total sensible thermal energy and
        // divide by the combined heat capacity instead of using a temperature
        // displacement from one body.
        double capacity1 = parameters.mass1() * parameters.specificHeat1();
        double capacity2 = parameters.mass2() * parameters.specificHeat2();
        double totalCapacity = capacity1 + capacity2;
        double initialThermalEnergy = capacity1 * parameters.initialTemperature1()
                + capacity2 * parameters.initialTemperature2();
        double equilibrium = initialThermalEnergy / totalCapacity;
        double heat1 = capacity1 * (equilibrium - parameters.initialTemperature1());
        double heat2 = capacity2 * (equilibrium - parameters.initialTemperature2());
        requirePositiveFinite("reference capacity1", capacity1);
        requirePositiveFinite("reference capacity2", capacity2);
        requirePositiveFinite("reference total capacity", totalCapacity);
        requireFinite("reference initial thermal energy", initialThermalEnergy);
        requirePositiveFinite("reference equilibriumTemperature", equilibrium);
        requireFinite("reference heat1", heat1);
        requireFinite("reference heat2", heat2);

        Map<String, Double> values = new LinkedHashMap<>();
        values.put("temperature1", equilibrium);
        values.put("temperature2", equilibrium);
        values.put("equilibriumTemperature", equilibrium);
        values.put("heat1", heat1);
        values.put("heat2", heat2);
        return new AnalyticalPoint(values);
    }

    private static List<Double> repeated(double value, int count) {
        return Collections.nCopies(count, value);
    }

    private static void requireUnit(CanonicalQuantityBag quantities, String key, String expectedUnit) {
        if (!expectedUnit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical unit for '" + key + "' must be " + expectedUnit);
        }
    }

    private static void requireParameters(Parameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Calorimetry parameters are required");
        }
    }

    private static void requireFinite(String outputKey, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Calorimetry calculation produced non-finite " + outputKey);
        }
    }

    private static void requirePositiveFinite(String outputKey, double value) {
        requireFinite(outputKey, value);
        if (value <= 0.0) {
            throw new IllegalArgumentException("Calorimetry calculation produced non-positive " + outputKey);
        }
    }

    /** Immutable input state bound once from the canonical quantities. */
    public record Parameters(double mass1, double specificHeat1, double initialTemperature1,
                             double mass2, double specificHeat2, double initialTemperature2) {
        public Parameters {
            if (!Double.isFinite(mass1) || mass1 <= 0.0
                    || !Double.isFinite(specificHeat1) || specificHeat1 <= 0.0
                    || !Double.isFinite(initialTemperature1) || initialTemperature1 <= 0.0
                    || !Double.isFinite(mass2) || mass2 <= 0.0
                    || !Double.isFinite(specificHeat2) || specificHeat2 <= 0.0
                    || !Double.isFinite(initialTemperature2) || initialTemperature2 <= 0.0) {
                throw new IllegalArgumentException(
                        "Calorimetry requires positive finite masses, specific heats, and absolute temperatures");
            }
        }
    }
}
