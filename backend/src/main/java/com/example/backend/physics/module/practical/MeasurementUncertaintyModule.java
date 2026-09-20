package com.example.backend.physics.module.practical;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed absolute and relative uncertainty interval for one measured scalar. */
public final class MeasurementUncertaintyModule
        implements PhysicsModule<MeasurementUncertaintyModule.Parameters> {
    public static final String MODULE_ID = "measurement_uncertainty";
    public static final String NUMERICAL_SOLVER_ID = "measurement_uncertainty_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "measurement_uncertainty_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        double measuredValue = requireFinite(quantities, "measured_value");
        double absoluteUncertainty = requireFinite(quantities, "absolute_uncertainty");
        String measuredUnit = quantities.unit("measured_value");
        String uncertaintyUnit = quantities.unit("absolute_uncertainty");
        if (!"1".equals(measuredUnit) || !Objects.equals(measuredUnit, uncertaintyUnit)) {
            throw new IllegalArgumentException(
                    "Measurement uncertainty outputs are dimensionless and both inputs must use unit 1");
        }
        return new Parameters(measuredValue, absoluteUncertainty);
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();
        double measuredValue = parameters.measuredValue();
        double uncertainty = parameters.absoluteUncertainty();
        double relativeUncertainty = measuredValue == 0.0
                ? 0.0 : Math.abs(uncertainty / measuredValue);
        double relativeDefined = measuredValue == 0.0 ? 0.0 : 1.0;
        double lowerBound = measuredValue - uncertainty;
        double upperBound = measuredValue + uncertainty;
        requireFiniteResult("relativeUncertainty", relativeUncertainty);
        requireFiniteResult("lowerBound", lowerBound);
        requireFiniteResult("upperBound", upperBound);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("measuredValue", repeated(measuredValue, time.size()));
        values.put("absoluteUncertainty", repeated(uncertainty, time.size()));
        values.put("relativeUncertainty", repeated(relativeUncertainty, time.size()));
        values.put("relativeUncertaintyDefined", repeated(relativeDefined, time.size()));
        values.put("lowerBound", repeated(lowerBound, time.size()));
        values.put("upperBound", repeated(upperBound, time.size()));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Measurement-uncertainty reference time must be finite and non-negative");
        }

        double center = parameters.measuredValue();
        double halfWidth = parameters.absoluteUncertainty();
        double magnitude = Math.abs(center);
        double scale = Math.max(magnitude, halfWidth);
        double relativeUncertainty = center == 0.0 ? 0.0
                : (halfWidth / scale) / (magnitude / scale);
        double lowerBound = BigDecimal.valueOf(center)
                .subtract(BigDecimal.valueOf(halfWidth)).doubleValue();
        double upperBound = BigDecimal.valueOf(center)
                .add(BigDecimal.valueOf(halfWidth)).doubleValue();
        double relativeDefined = center == 0.0 ? 0.0 : 1.0;
        requireFiniteResult("reference relativeUncertainty", relativeUncertainty);
        requireFiniteResult("reference lowerBound", lowerBound);
        requireFiniteResult("reference upperBound", upperBound);

        Map<String, Double> values = new LinkedHashMap<>();
        values.put("measuredValue", center);
        values.put("absoluteUncertainty", halfWidth);
        values.put("relativeUncertainty", relativeUncertainty);
        values.put("relativeUncertaintyDefined", relativeDefined);
        values.put("lowerBound", lowerBound);
        values.put("upperBound", upperBound);
        return new AnalyticalPoint(values);
    }

    private static double requireFinite(CanonicalQuantityBag quantities, String key) {
        String unit = quantities.unit(key);
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("Canonical measurement quantity unit is required: " + key);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Canonical measurement quantity must be finite: " + key);
        }
        return value;
    }

    private static List<Double> repeated(double value, int count) {
        return Collections.nCopies(count, value);
    }

    private static void requireFiniteResult(String outputKey, double value) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Measurement uncertainty result is not finite: " + outputKey);
        }
    }

    /** Canonical scalar measurement inputs; units must match in the bound quantity contract. */
    public record Parameters(double measuredValue, double absoluteUncertainty) {
        public Parameters {
            if (!Double.isFinite(measuredValue) || !Double.isFinite(absoluteUncertainty)
                    || absoluteUncertainty < 0.0) {
                throw new IllegalArgumentException("Measured value must be finite and absolute uncertainty non-negative");
            }
        }
    }
}
