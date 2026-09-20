package com.example.backend.physics.module.thermal;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed owner of the linear thermal expansion model. */
public final class ThermalExpansionModule implements PhysicsModule<ThermalExpansionModule.Parameters> {
    public static final String MODULE_ID = "thermal_expansion";
    public static final String NUMERICAL_SOLVER_ID = "thermal_expansion_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "thermal_expansion_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        if (quantities == null) throw new IllegalArgumentException("Canonical thermal quantities are required");
        requireUnit(quantities, "initial_length", "m");
        requireUnit(quantities, "linear_expansion_coefficient", "1/K");
        requireUnit(quantities, "initial_temperature", "K");
        requireUnit(quantities, "final_temperature", "K");
        return new Parameters(quantities.require("initial_length"),
                quantities.require("linear_expansion_coefficient"),
                quantities.require("initial_temperature"), quantities.require("final_temperature"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        requireParameters(parameters);
        if (clock == null) throw new IllegalArgumentException("Thermal expansion simulation clock is required");

        double deltaTemperature = parameters.finalTemperature() - parameters.initialTemperature();
        requireFinite(deltaTemperature, "temperature change");
        double thermalStrain = parameters.coefficient() * deltaTemperature;
        requireFinite(thermalStrain, "thermal strain");
        double extension = parameters.initialLength() * thermalStrain;
        requireFinite(extension, "extension");
        double finalLength = parameters.initialLength() + extension;
        requirePhysicalFinalLength(finalLength);

        List<Double> time = clock.sampleTimes();
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("deltaTemperature", repeated(deltaTemperature, time.size()));
        values.put("extension", repeated(extension, time.size()));
        values.put("finalLength", repeated(finalLength, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        requireParameters(parameters);
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Reference time must be finite and non-negative");
        }

        // Independent algebraic form: scale the original length by the total
        // expansion ratio, then derive the extension from the resulting length.
        double temperatureDifference = parameters.finalTemperature() - parameters.initialTemperature();
        requireFinite(temperatureDifference, "reference temperature change");
        double scaleFactor = 1.0 + parameters.coefficient() * temperatureDifference;
        requireFinite(scaleFactor, "reference expansion ratio");
        double lengthAfterHeating = parameters.initialLength() * scaleFactor;
        requirePhysicalFinalLength(lengthAfterHeating);
        double lengthChange = lengthAfterHeating - parameters.initialLength();
        requireFinite(lengthChange, "reference extension");
        return new AnalyticalPoint(Map.of(
                "deltaTemperature", temperatureDifference,
                "extension", lengthChange,
                "finalLength", lengthAfterHeating));
    }

    private static List<Double> repeated(double value, int count) {
        return java.util.Collections.nCopies(count, value);
    }

    private static void requireUnit(CanonicalQuantityBag quantities, String key, String expected) {
        if (!expected.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical unit for " + key + " must be " + expected);
        }
    }

    private static void requireParameters(Parameters parameters) {
        if (parameters == null) throw new IllegalArgumentException("Thermal expansion parameters are required");
    }

    private static void requireFinite(double value, String quantity) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Thermal expansion produced non-finite " + quantity);
    }

    private static void requirePhysicalFinalLength(double value) {
        requireFinite(value, "final length");
        if (value <= 0.0) throw new IllegalArgumentException("Thermal expansion final length must be positive");
    }

    /** Immutable primitive state bound once from canonical quantities. */
    public record Parameters(double initialLength, double coefficient,
                             double initialTemperature, double finalTemperature) {
        public Parameters {
            if (!Double.isFinite(initialLength) || initialLength <= 0.0
                    || !Double.isFinite(coefficient) || coefficient < 0.0
                    || !Double.isFinite(initialTemperature) || initialTemperature <= 0.0
                    || !Double.isFinite(finalTemperature) || finalTemperature <= 0.0) {
                throw new IllegalArgumentException(
                        "Thermal expansion requires positive length and absolute temperatures and a non-negative coefficient");
            }
        }
    }
}
