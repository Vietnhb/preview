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

/** Typed conversion module for Celsius, Kelvin, and Fahrenheit. */
public final class TemperatureScalesModule implements PhysicsModule<TemperatureScalesModule.Parameters> {
    public static final String MODULE_ID = "temperature_scales";
    public static final String NUMERICAL_SOLVER_ID = "temperature_scale_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "temperature_scale_reference_v2";
    private static final String KELVIN = "kelvin";
    private static final String FAHRENHEIT = "fahrenheit";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        if (quantities == null) {
            throw new IllegalArgumentException("Canonical temperature-scale quantity is required");
        }
        if (!"degC".equals(quantities.unit("temperature_celsius"))) {
            throw new IllegalArgumentException("Canonical unit for 'temperature_celsius' must be degC");
        }
        return new Parameters(quantities.require("temperature_celsius"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        requireParameters(parameters);
        if (clock == null) {
            throw new IllegalArgumentException("Temperature-scale simulation clock is required");
        }

        // Numerical path uses the direct affine conversions from Celsius.
        double kelvin = parameters.celsius() + 273.15;
        double fahrenheit = parameters.celsius() * (9.0 / 5.0) + 32.0;
        requireFinite(KELVIN, kelvin);
        requireFinite(FAHRENHEIT, fahrenheit);

        List<Double> time = clock.sampleTimes();
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("celsius", repeated(parameters.celsius(), time.size()));
        values.put(KELVIN, repeated(kelvin, time.size()));
        values.put(FAHRENHEIT, repeated(fahrenheit, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        requireParameters(parameters);
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Reference time must be finite and non-negative");
        }

        // Independent form: convert via absolute temperature, then apply the
        // Fahrenheit offset defined relative to absolute zero.
        double kelvin = parameters.celsius() - (-273.15);
        double fahrenheit = kelvin * (9.0 / 5.0) - 459.67;
        requireFinite("reference " + KELVIN, kelvin);
        requireFinite("reference " + FAHRENHEIT, fahrenheit);

        Map<String, Double> values = new LinkedHashMap<>();
        values.put("celsius", parameters.celsius());
        values.put(KELVIN, kelvin);
        values.put(FAHRENHEIT, fahrenheit);
        return new AnalyticalPoint(values);
    }

    private static List<Double> repeated(double value, int count) {
        return Collections.nCopies(count, value);
    }

    private static void requireParameters(Parameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Temperature-scale parameters are required");
        }
    }

    private static void requireFinite(String outputKey, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Temperature-scale conversion produced non-finite " + outputKey);
        }
    }

    /** Immutable Celsius input after canonical binding. */
    public record Parameters(double celsius) {
        public Parameters {
            if (!Double.isFinite(celsius) || celsius < -273.15) {
                throw new IllegalArgumentException("Temperature must be finite and not below absolute zero");
            }
        }
    }
}
