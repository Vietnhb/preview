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

class NuclearEnergyModuleTest {
    private static final double ENERGY_TOLERANCE = 1.0e-26;
    private final NuclearEnergyModule module = new NuclearEnergyModule();

    @Test
    void typedBindingMatchesGoldenMassDefectEnergyAndIndependentReference() {
        assertEquals("nuclear_energy_solver_v2", module.numericalSolverId());
        assertEquals("nuclear_energy_reference_v2", module.referenceSolverId());
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                NuclearEnergyModule.NUMERICAL_SOLVER_ID,
                NuclearEnergyModule.REFERENCE_SOLVER_ID,
                quantities(1.0e-30, 2.0));

        SolverOutput output = bound.solve(new SimulationClock(1.0, 0.5));
        assertEquals(List.of(0.0, 0.5, 1.0), output.time());
        assertEquals(Set.of("releasedEnergy", "massDefect"), output.values().keySet());
        assertSeries(output, "releasedEnergy", 1.7975103574736353e-13, ENERGY_TOLERANCE);
        assertSeries(output, "massDefect", 1.0e-30, 1.0e-40);

        Map<String, Double> reference = bound.reference(0.5).values();
        assertEquals(output.values().keySet(), reference.keySet());
        assertEquals(1.7975103574736353e-13, reference.get("releasedEnergy"), ENERGY_TOLERANCE);
        assertEquals(1.0e-30, reference.get("massDefect"), 1.0e-40);
    }

    @Test
    void zeroMassDefectIsAllowedButReactionCountMustBeAPositiveInteger() {
        var zeroEnergy = module.bind(quantities(0.0, 3.0));
        SolverOutput output = module.solve(zeroEnergy, new SimulationClock(0.2, 0.1));
        assertEquals(List.of(0.0, 0.0, 0.0), output.values().get("releasedEnergy"));
        assertEquals(List.of(0.0, 0.0, 0.0), output.values().get("massDefect"));

        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0e-30, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0e-30, 0.5)));
        assertThrows(IllegalArgumentException.class,
                () -> new NuclearEnergyModule.Parameters(1.0e-30, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new NuclearEnergyModule.Parameters(1.0e-30, 2.5));
    }

    @Test
    void requiresCompiledReactionCountAndRejectsInvalidDomainsAndUnits() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        CanonicalQuantityBag missingReactionCount = new CanonicalQuantityBag(
                Map.of("mass_defect", BigDecimal.valueOf(1.0e-30)),
                Map.of("mass_defect", "kg"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(missingReactionCount));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(-1.0, 1.0)));

        CanonicalQuantityBag wrongUnit = new CanonicalQuantityBag(
                Map.of("mass_defect", BigDecimal.valueOf(1.0e-30), "reaction_count", BigDecimal.ONE),
                Map.of("mass_defect", "g", "reaction_count", "1"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));

        assertThrows(IllegalArgumentException.class,
                () -> new NuclearEnergyModule.Parameters(Double.POSITIVE_INFINITY, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new NuclearEnergyModule.Parameters(1.0, 0.0));
    }

    @Test
    void rejectsInvalidReferenceTimesAndUnrepresentableEnergy() {
        var valid = module.bind(quantities(1.0e-30, 1.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(valid, Double.POSITIVE_INFINITY));

        var overflowing = module.bind(quantities(1.0e308, 2.0));
        assertThrows(ArithmeticException.class,
                () -> module.solve(overflowing, new SimulationClock(0.1, 0.1)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(overflowing, 0.0));
    }

    private static void assertSeries(SolverOutput output, String key, double expected, double tolerance) {
        List<Double> actual = output.values().get(key);
        assertEquals(output.time().size(), actual.size(), key);
        for (double value : actual) assertEquals(expected, value, tolerance, key);
    }

    private static CanonicalQuantityBag quantities(double massDefect, double reactionCount) {
        return new CanonicalQuantityBag(
                Map.of("mass_defect", BigDecimal.valueOf(massDefect),
                        "reaction_count", BigDecimal.valueOf(reactionCount)),
                Map.of("mass_defect", "kg", "reaction_count", "1"));
    }
}
