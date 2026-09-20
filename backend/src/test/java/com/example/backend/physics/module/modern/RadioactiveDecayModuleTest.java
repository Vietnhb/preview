package com.example.backend.physics.module.modern;

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

class RadioactiveDecayModuleTest {
    private static final double TOLERANCE = 1.0e-10;
    private final RadioactiveDecayModule module = new RadioactiveDecayModule();

    @Test
    void registeredModuleMatchesHandCalculatedHalfLifeAndIndependentReference() {
        assertEquals("radioactive_decay_solver_v2", module.numericalSolverId());
        assertEquals("radioactive_decay_reference_v2", module.referenceSolverId());
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                RadioactiveDecayModule.NUMERICAL_SOLVER_ID,
                RadioactiveDecayModule.REFERENCE_SOLVER_ID,
                quantities(800.0, Math.log(2.0)));

        SolverOutput output = bound.solve(new SimulationClock(2.0, 0.5));
        assertEquals(List.of(0.0, 0.5, 1.0, 1.5, 2.0), output.time());
        assertEquals(Set.of("remainingCount", "activity"), output.values().keySet());
        assertSeries(output, "remainingCount", 0, 800.0);
        assertSeries(output, "remainingCount", 2, 400.0);
        assertSeries(output, "remainingCount", 4, 200.0);
        assertSeries(output, "activity", 2, 400.0 * Math.log(2.0));

        for (int index = 0; index < output.time().size(); index++) {
            int sampleIndex = index;
            Map<String, Double> reference = bound.reference(output.time().get(index)).values();
            output.values().forEach((key, series) ->
                    assertEquals(series.get(sampleIndex), reference.get(key), TOLERANCE, key));
        }
    }

    @Test
    void stableNuclideAndZeroInitialCountAreValidBoundaries() {
        SolverOutput stable = module.solve(module.bind(quantities(25.0, 0.0)), new SimulationClock(1.0, 0.5));
        assertEquals(List.of(25.0, 25.0, 25.0), stable.values().get("remainingCount"));
        assertEquals(List.of(0.0, 0.0, 0.0), stable.values().get("activity"));

        SolverOutput empty = module.solve(module.bind(quantities(0.0, 3.0)), new SimulationClock(0.2, 0.1));
        assertEquals(List.of(0.0, 0.0, 0.0), empty.values().get("remainingCount"));
        assertEquals(List.of(0.0, 0.0, 0.0), empty.values().get("activity"));
        assertEquals(0.0, module.referenceAt(module.bind(quantities(0.0, 3.0)), 1.0)
                .values().get("remainingCount"), TOLERANCE);
    }

    @Test
    void rejectsMissingValuesWrongUnitsNegativeInputsAndInvalidReferenceTimes() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        CanonicalQuantityBag wrongUnit = new CanonicalQuantityBag(
                Map.of("initial_count", BigDecimal.valueOf(10), "decay_constant", BigDecimal.ONE),
                Map.of("initial_count", "mol", "decay_constant", "1/s"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(-1.0, 0.1)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, -0.1)));
        assertThrows(IllegalArgumentException.class,
                () -> new RadioactiveDecayModule.Parameters(Double.NaN, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new RadioactiveDecayModule.Parameters(1.0, Double.POSITIVE_INFINITY));

        var valid = module.bind(quantities(1.0, 0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(valid, Double.POSITIVE_INFINITY));
    }

    @Test
    void rejectsNonFiniteDerivedActivityAndKeepsOrdinaryOutputsFinite() {
        var extreme = module.bind(quantities(1.0e308, 1.0e308));
        assertThrows(ArithmeticException.class, () -> module.solve(extreme, new SimulationClock(0.1, 0.1)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(extreme, 0.0));

        SolverOutput ordinary = module.solve(module.bind(quantities(1000.0, Math.log(2.0))),
                new SimulationClock(1.0, 0.25));
        assertTrue(ordinary.values().values().stream().flatMap(List::stream).allMatch(Double::isFinite));
    }

    private static void assertSeries(SolverOutput output, String key, int index, double expected) {
        assertEquals(output.time().size(), output.values().get(key).size(), key);
        assertEquals(expected, output.values().get(key).get(index), TOLERANCE, key);
    }

    private static CanonicalQuantityBag quantities(double initialCount, double decayConstant) {
        return new CanonicalQuantityBag(
                Map.of("initial_count", BigDecimal.valueOf(initialCount),
                        "decay_constant", BigDecimal.valueOf(decayConstant)),
                Map.of("initial_count", "1", "decay_constant", "1/s"));
    }
}
