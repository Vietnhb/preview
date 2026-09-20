package com.example.backend.physics.module.circuits;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.BoundPhysicsModule;
import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResistorsParallelModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private static final Set<String> OUTPUT_KEYS = Set.of(
            "equivalentResistance", "totalCurrent", "branchCurrent1", "branchCurrent2");

    @Test
    void goldenParallelCircuitSatisfiesKclAndIndependentReferenceAtEverySample() {
        ResistorsParallelModule module = new ResistorsParallelModule();
        BoundPhysicsModule bound = new PhysicsModuleRegistry(java.util.List.of(module)).bind(
                "resistors_parallel_solver", "resistors_parallel_reference", quantities(12, 2, 4));
        SolverOutput output = bound.solve(new SimulationClock(0.4, 0.2));

        assertEquals("resistors_parallel", bound.moduleId());
        assertEquals(java.util.List.of(0.0, 0.2, 0.4), output.time());
        assertEquals(OUTPUT_KEYS, output.values().keySet());
        for (int i = 0; i < output.time().size(); i++) {
            assertEquals(4.0 / 3.0, output.values().get("equivalentResistance").get(i), TOLERANCE);
            assertEquals(9.0, output.values().get("totalCurrent").get(i), TOLERANCE);
            assertEquals(6.0, output.values().get("branchCurrent1").get(i), TOLERANCE);
            assertEquals(3.0, output.values().get("branchCurrent2").get(i), TOLERANCE);
            assertEquals(output.values().get("branchCurrent1").get(i)
                    + output.values().get("branchCurrent2").get(i),
                    output.values().get("totalCurrent").get(i), TOLERANCE);
            var reference = bound.reference(output.time().get(i)).values();
            for (String key : OUTPUT_KEYS) {
                assertEquals(output.values().get(key).get(i), reference.get(key), TOLERANCE, key);
            }
        }
    }

    @Test
    void zeroAndSignedSourceVoltageRemainValidParallelCircuitInputs() {
        ResistorsParallelModule module = new ResistorsParallelModule();
        var zero = module.bind(quantities(0, 2, 4));
        var negative = module.bind(quantities(-12, 2, 4));

        SolverOutput zeroOutput = module.solve(zero, new SimulationClock(0.2, 0.2));
        SolverOutput negativeOutput = module.solve(negative, new SimulationClock(0.2, 0.2));
        assertEquals(0.0, zeroOutput.values().get("totalCurrent").get(0), TOLERANCE);
        assertEquals(-6.0, negativeOutput.values().get("branchCurrent1").get(0), TOLERANCE);
        assertEquals(-3.0, negativeOutput.values().get("branchCurrent2").get(0), TOLERANCE);
        assertEquals(-9.0, negativeOutput.values().get("totalCurrent").get(0), TOLERANCE);
    }

    @Test
    void stableParallelEquivalentHandlesLargeFiniteResistancesAndEqualBranches() {
        ResistorsParallelModule module = new ResistorsParallelModule();
        var large = module.bind(quantities(12, 1.0e308, 1.0e308));
        var equal = module.bind(quantities(10, 10, 10));

        SolverOutput largeOutput = module.solve(large, new SimulationClock(0.1, 0.1));
        SolverOutput equalOutput = module.solve(equal, new SimulationClock(0.1, 0.1));
        assertEquals(5.0e307, largeOutput.values().get("equivalentResistance").get(0), 1.0e294);
        assertEquals(5.0, equalOutput.values().get("equivalentResistance").get(0), TOLERANCE);
        assertEquals(1.0, equalOutput.values().get("branchCurrent1").get(0), TOLERANCE);
        assertEquals(1.0, equalOutput.values().get("branchCurrent2").get(0), TOLERANCE);
    }

    @Test
    void rejectsInvalidResistanceAndNonFiniteDerivedCurrents() {
        ResistorsParallelModule module = new ResistorsParallelModule();
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(12, 0, 4)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(12, -2, 4)));
        assertThrows(IllegalArgumentException.class,
                () -> new ResistorsParallelModule.Parameters(Double.POSITIVE_INFINITY, 2, 4));

        var overflow = module.bind(quantities(1.0e308, 1.0e-300, 1.0e-300));
        assertThrows(ArithmeticException.class, () -> module.solve(overflow, new SimulationClock(0.1, 0.1)));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(new ResistorsParallelModule.Parameters(12, 2, 4), -1));
    }

    @Test
    void everyDeclaredParallelOutputIsFiniteAndHasTheRequestedShape() {
        ResistorsParallelModule module = new ResistorsParallelModule();
        SolverOutput output = module.solve(module.bind(quantities(120, 10, 20)), new SimulationClock(1, 0.25));

        assertEquals(5, output.time().size());
        assertEquals(OUTPUT_KEYS, output.values().keySet());
        output.values().forEach((key, values) -> {
            assertEquals(output.time().size(), values.size(), key);
            assertTrue(values.stream().allMatch(Double::isFinite), key);
        });
    }

    private static CanonicalQuantityBag quantities(double voltage, double resistance1, double resistance2) {
        return new CanonicalQuantityBag(
                Map.of("voltage", BigDecimal.valueOf(voltage),
                        "resistance_1", BigDecimal.valueOf(resistance1),
                        "resistance_2", BigDecimal.valueOf(resistance2)),
                Map.of("voltage", "V", "resistance_1", "ohm", "resistance_2", "ohm"));
    }
}
