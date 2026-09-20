package com.example.backend.physics.module.practical;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.BoundPhysicsModule;
import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasurementUncertaintyModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private final MeasurementUncertaintyModule module = new MeasurementUncertaintyModule();

    @Test
    void typedBindingMatchesHandCalculatedIntervalAndIndependentReference() {
        assertEquals("measurement_uncertainty", module.moduleId());
        assertEquals("measurement_uncertainty_solver_v2", module.numericalSolverId());
        assertEquals("measurement_uncertainty_reference_v2", module.referenceSolverId());
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                MeasurementUncertaintyModule.NUMERICAL_SOLVER_ID,
                MeasurementUncertaintyModule.REFERENCE_SOLVER_ID,
                quantities(10.0, 0.2, "1", "1"));

        SolverOutput output = bound.solve(new SimulationClock(0.2, 0.1));
        assertEquals(List.of(0.0, 0.1, 0.2), output.time());
        assertEquals(Set.of("measuredValue", "absoluteUncertainty", "relativeUncertainty",
                "relativeUncertaintyDefined", "lowerBound", "upperBound"), output.values().keySet());
        assertSeries(output, "measuredValue", 10.0);
        assertSeries(output, "absoluteUncertainty", 0.2);
        assertSeries(output, "relativeUncertainty", 0.02);
        assertSeries(output, "relativeUncertaintyDefined", 1.0);
        assertSeries(output, "lowerBound", 9.8);
        assertSeries(output, "upperBound", 10.2);

        for (int index = 0; index < output.time().size(); index++) {
            int sampleIndex = index;
            Map<String, Double> reference = bound.reference(output.time().get(index)).values();
            output.values().forEach((key, series) ->
                    assertEquals(series.get(sampleIndex), reference.get(key), TOLERANCE, key));
        }
    }

    @Test
    void zeroMeasurementMarksRelativeUncertaintyUndefinedAndZeroUncertaintyIsValid() {
        var zeroMeasurement = module.bind(quantities(0.0, 0.4, "1", "1"));
        SolverOutput zeroOutput = module.solve(zeroMeasurement, new SimulationClock(0.1, 0.1));
        assertSeries(zeroOutput, "relativeUncertainty", 0.0, 0.0);
        assertSeries(zeroOutput, "relativeUncertaintyDefined", 0.0, 0.0);
        assertSeries(zeroOutput, "lowerBound", -0.4, -0.4);
        assertSeries(zeroOutput, "upperBound", 0.4, 0.4);
        assertEquals(0.0, module.referenceAt(zeroMeasurement, 0.0).values().get("relativeUncertainty"));

        var exact = module.bind(quantities(-5.0, 0.0, "1", "1"));
        SolverOutput exactOutput = module.solve(exact, new SimulationClock(0.1, 0.1));
        assertSeries(exactOutput, "relativeUncertainty", 0.0, 0.0);
        assertSeries(exactOutput, "relativeUncertaintyDefined", 1.0, 1.0);
        assertSeries(exactOutput, "lowerBound", -5.0, -5.0);
        assertSeries(exactOutput, "upperBound", -5.0, -5.0);
    }

    @Test
    void rejectsMissingValuesNegativeUncertaintyMismatchedUnitsAndInvalidReferenceTime() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(3.0, -0.1, "m", "m")));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(3.0, 0.1, "m", "s")));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(3.0, 0.1, "m", "m")));
        assertThrows(IllegalArgumentException.class,
                () -> new MeasurementUncertaintyModule.Parameters(Double.NaN, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new MeasurementUncertaintyModule.Parameters(1.0, Double.POSITIVE_INFINITY));

        var valid = module.bind(quantities(3.0, 0.1, "1", "1"));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(valid, Double.POSITIVE_INFINITY));
    }

    @Test
    void rejectsNonFiniteRelativeUncertaintyAndIntervalBounds() {
        var overflowingRatio = module.bind(quantities(Double.MIN_VALUE, Double.MAX_VALUE, "1", "1"));
        assertThrows(ArithmeticException.class,
                () -> module.solve(overflowingRatio, new SimulationClock(0.1, 0.1)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(overflowingRatio, 0.0));

        var overflowingUpperBound = module.bind(quantities(Double.MAX_VALUE, Double.MAX_VALUE, "1", "1"));
        assertThrows(ArithmeticException.class,
                () -> module.solve(overflowingUpperBound, new SimulationClock(0.1, 0.1)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(overflowingUpperBound, 0.0));

        SolverOutput finite = module.solve(module.bind(quantities(-4.0, 0.5, "1", "1")),
                new SimulationClock(0.2, 0.1));
        assertTrue(finite.values().values().stream().flatMap(List::stream).allMatch(Double::isFinite));
    }

    private static void assertSeries(SolverOutput output, String key, double expected) {
        List<Double> actual = output.values().get(key);
        assertEquals(output.time().size(), actual.size(), key);
        for (double value : actual) assertEquals(expected, value, TOLERANCE, key);
    }

    private static void assertSeries(SolverOutput output, String key, double first, double second) {
        List<Double> actual = output.values().get(key);
        assertEquals(List.of(first, second), actual, key);
    }

    private static CanonicalQuantityBag quantities(double measuredValue, double absoluteUncertainty,
                                                   String measuredUnit, String uncertaintyUnit) {
        return new CanonicalQuantityBag(
                Map.of("measured_value", decimal(measuredValue),
                        "absolute_uncertainty", decimal(absoluteUncertainty)),
                Map.of("measured_value", measuredUnit,
                        "absolute_uncertainty", uncertaintyUnit));
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }
}
