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

/** Typed implementation of the first-law energy balance, with work positive out of the system. */
public final class FirstLawThermodynamicsModule
        implements PhysicsModule<FirstLawThermodynamicsModule.Parameters> {
    public static final String MODULE_ID = "first_law_thermodynamics";
    public static final String NUMERICAL_SOLVER_ID = "first_law_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "first_law_reference_v2";
    private static final String DELTA_INTERNAL_ENERGY = "deltaInternalEnergy";
    private static final String INTERNAL_ENERGY = "internalEnergy";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        if (quantities == null) {
            throw new IllegalArgumentException("Canonical first-law quantities are required");
        }
        requireUnit(quantities, "initial_internal_energy", "J");
        requireUnit(quantities, "heat_added", "J");
        requireUnit(quantities, "work_done", "J");
        return new Parameters(
                quantities.require("initial_internal_energy"),
                quantities.require("heat_added"),
                quantities.require("work_done"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        requireParameters(parameters);
        if (clock == null) {
            throw new IllegalArgumentException("First-law simulation clock is required");
        }

        // Numerical path applies the ΔU = Q - W balance, then updates U.
        double deltaInternalEnergy = parameters.heatAdded() - parameters.workDone();
        double finalInternalEnergy = parameters.initialInternalEnergy() + deltaInternalEnergy;
        requireFinite(DELTA_INTERNAL_ENERGY, deltaInternalEnergy);
        requireFinite(INTERNAL_ENERGY, finalInternalEnergy);

        List<Double> time = clock.sampleTimes();
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put(INTERNAL_ENERGY, repeated(finalInternalEnergy, time.size()));
        values.put(DELTA_INTERNAL_ENERGY, repeated(deltaInternalEnergy, time.size()));
        values.put("heat", repeated(parameters.heatAdded(), time.size()));
        values.put("work", repeated(parameters.workDone(), time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        requireParameters(parameters);
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Reference time must be finite and non-negative");
        }

        // Oracle books heat into the initial state, then removes work from the
        // resulting energy. Derive the energy change from the two states.
        double energyAfterHeat = parameters.initialInternalEnergy() + parameters.heatAdded();
        double finalInternalEnergy = energyAfterHeat - parameters.workDone();
        double deltaInternalEnergy = finalInternalEnergy - parameters.initialInternalEnergy();
        requireFinite("reference energy after heat", energyAfterHeat);
        requireFinite("reference " + INTERNAL_ENERGY, finalInternalEnergy);
        requireFinite("reference " + DELTA_INTERNAL_ENERGY, deltaInternalEnergy);

        Map<String, Double> values = new LinkedHashMap<>();
        values.put(INTERNAL_ENERGY, finalInternalEnergy);
        values.put(DELTA_INTERNAL_ENERGY, deltaInternalEnergy);
        values.put("heat", parameters.heatAdded());
        values.put("work", parameters.workDone());
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
            throw new IllegalArgumentException("First-law parameters are required");
        }
    }

    private static void requireFinite(String outputKey, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("First-law calculation produced non-finite " + outputKey);
        }
    }

    /** Immutable energy values bound once from canonical quantities. */
    public record Parameters(double initialInternalEnergy, double heatAdded, double workDone) {
        public Parameters {
            if (!Double.isFinite(initialInternalEnergy)
                    || !Double.isFinite(heatAdded)
                    || !Double.isFinite(workDone)) {
                throw new IllegalArgumentException("First-law energy quantities must be finite");
            }
        }
    }
}
