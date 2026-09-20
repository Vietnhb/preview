package com.example.backend.physics.module.dynamics;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkEnergyPowerModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private static final Set<String> OUTPUT_KEYS = Set.of(
            "workByForce", "initialKineticEnergy", "finalKineticEnergy",
            "deltaKineticEnergy", "averagePower");

    @Test
    void handComputedGoldenMatchesIndependentReferenceAndWorkEnergyInvariants() {
        WorkEnergyPowerModule module = new WorkEnergyPowerModule();
        var parameters = module.bind(quantities(2, 3, 5, 10, 4, Math.PI / 3, 2));
        SolverOutput output = module.solve(parameters, new SimulationClock(0.4, 0.2));

        assertEquals("work_energy_power", module.moduleId());
        assertEquals("work_energy_power_solver", module.numericalSolverId());
        assertEquals("work_energy_power_reference", module.referenceSolverId());
        assertEquals(java.util.List.of(0.0, 0.2, 0.4), output.time());
        assertEquals(OUTPUT_KEYS, output.values().keySet());

        for (int index = 0; index < output.time().size(); index++) {
            assertEquals(20.0, output.values().get("workByForce").get(index), TOLERANCE);
            assertEquals(9.0, output.values().get("initialKineticEnergy").get(index), TOLERANCE);
            assertEquals(25.0, output.values().get("finalKineticEnergy").get(index), TOLERANCE);
            assertEquals(16.0, output.values().get("deltaKineticEnergy").get(index), TOLERANCE);
            assertEquals(10.0, output.values().get("averagePower").get(index), TOLERANCE);
            assertEquals(output.values().get("finalKineticEnergy").get(index)
                            - output.values().get("initialKineticEnergy").get(index),
                    output.values().get("deltaKineticEnergy").get(index), TOLERANCE);
            assertEquals(output.values().get("workByForce").get(index),
                    output.values().get("averagePower").get(index) * parameters.duration(), TOLERANCE);

            var reference = module.referenceAt(parameters, output.time().get(index)).values();
            for (String key : OUTPUT_KEYS) {
                assertEquals(output.values().get(key).get(index), reference.get(key), TOLERANCE, key);
            }
        }
    }

    @Test
    void acceptsAngleEndpointsAndZeroMagnitudeInputsWithinTheDeclaredDomain() {
        WorkEnergyPowerModule module = new WorkEnergyPowerModule();
        var zero = module.bind(quantities(1, 0, 0, 0, 0, 0, 1));
        var zeroOutput = module.solve(zero, new SimulationClock(0.2, 0.2));
        assertEquals(0.0, zeroOutput.values().get("workByForce").getFirst(), TOLERANCE);
        assertEquals(0.0, zeroOutput.values().get("deltaKineticEnergy").getFirst(), TOLERANCE);
        assertEquals(0.0, module.referenceAt(zero, 0).values().get("averagePower"), TOLERANCE);

        var opposite = module.bind(quantities(1, 0, 0, 3, 2, Math.PI, 1));
        assertEquals(-6.0, module.solve(opposite, new SimulationClock(0.1, 0.1))
                .values().get("workByForce").getFirst(), TOLERANCE);
    }

    @Test
    void rejectsNonCanonicalUnitsAndInvalidOrNonFiniteDomains() {
        WorkEnergyPowerModule module = new WorkEnergyPowerModule();
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(2, 3, 5, 10, 4, 0, 2, "force", "kg")));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0, 3, 5, 10, 4, 0, 2)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(2, -1, 5, 10, 4, 0, 2)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(2, 3, 5, -1, 4, 0, 2)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(2, 3, 5, 10, -1, 0, 2)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(2, 3, 5, 10, 4, -0.01, 2)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(2, 3, 5, 10, 4, Math.PI + 0.01, 2)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(2, 3, 5, 10, 4, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> new WorkEnergyPowerModule.Parameters(
                Double.NaN, 0, 0, 0, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(
                new WorkEnergyPowerModule.Parameters(1, 0, 0, 0, 0, 0, 1), Double.NaN));
    }

    @Test
    void rejectsOverflowingResultsAndPreservesFiniteOutputShape() {
        WorkEnergyPowerModule module = new WorkEnergyPowerModule();
        var overflowingWork = module.bind(quantities(1, 0, 0, Double.MAX_VALUE, 2, 0, 1));
        assertThrows(ArithmeticException.class,
                () -> module.solve(overflowingWork, new SimulationClock(0.1, 0.1)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(overflowingWork, 0));

        var overflowingEnergy = module.bind(quantities(1, Double.MAX_VALUE, 0, 0, 0, 0, 1));
        assertThrows(ArithmeticException.class,
                () -> module.solve(overflowingEnergy, new SimulationClock(0.1, 0.1)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(overflowingEnergy, 0));

        var overflowingPower = module.bind(quantities(1, 0, 0, Double.MAX_VALUE, 1, 0, 1.0e-308));
        assertThrows(ArithmeticException.class,
                () -> module.solve(overflowingPower, new SimulationClock(0.1, 0.1)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(overflowingPower, 0));

        SolverOutput finite = module.solve(module.bind(quantities(5, 1, 3, 4, 2, Math.PI / 2, 2)),
                new SimulationClock(1, 0.25));
        assertEquals(5, finite.time().size());
        assertEquals(OUTPUT_KEYS, finite.values().keySet());
        finite.values().forEach((key, values) -> {
            assertEquals(finite.time().size(), values.size(), key);
            assertTrue(values.stream().allMatch(Double::isFinite), key);
        });
    }

    private static CanonicalQuantityBag quantities(double mass, double initialSpeed, double finalSpeed,
                                                   double force, double displacement, double angle,
                                                   double duration) {
        return quantities(mass, initialSpeed, finalSpeed, force, displacement, angle, duration,
                null, null);
    }

    private static CanonicalQuantityBag quantities(double mass, double initialSpeed, double finalSpeed,
                                                   double force, double displacement, double angle,
                                                   double duration, String overrideKey, String overrideUnit) {
        Map<String, BigDecimal> values = Map.of(
                "mass", BigDecimal.valueOf(mass),
                "initial_speed", BigDecimal.valueOf(initialSpeed),
                "final_speed", BigDecimal.valueOf(finalSpeed),
                "force", BigDecimal.valueOf(force),
                "displacement", BigDecimal.valueOf(displacement),
                "force_angle", BigDecimal.valueOf(angle),
                "duration", BigDecimal.valueOf(duration));
        Map<String, String> units = new java.util.LinkedHashMap<>(Map.of(
                "mass", "kg",
                "initial_speed", "m/s",
                "final_speed", "m/s",
                "force", "N",
                "displacement", "m",
                "force_angle", "rad",
                "duration", "s"));
        if (overrideKey != null) units.put(overrideKey, overrideUnit);
        return new CanonicalQuantityBag(values, units);
    }
}
