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

class DiodeCharacteristicModuleTest {
    private static final double THERMAL_VOLTAGE_300K = 0.025851999786435535;
    private static final double TOLERANCE = 1.0e-24;
    private static final Set<String> OUTPUT_KEYS = Set.of("thermalVoltage", "current", "power");

    @Test
    void goldenOneThermalVoltageCaseMatchesTheIndependentShockleyOracle() {
        DiodeCharacteristicModule module = new DiodeCharacteristicModule();
        BoundPhysicsModule bound = new PhysicsModuleRegistry(java.util.List.of(module)).bind(
                DiodeCharacteristicModule.NUMERICAL_SOLVER_ID,
                DiodeCharacteristicModule.REFERENCE_SOLVER_ID,
                quantities(THERMAL_VOLTAGE_300K, 1.0e-12, 1.0, 300.0));
        SolverOutput output = bound.solve(new SimulationClock(0.2, 0.1));

        assertEquals(DiodeCharacteristicModule.MODULE_ID, bound.moduleId());
        assertEquals(java.util.List.of(0.0, 0.1, 0.2), output.time());
        assertEquals(OUTPUT_KEYS, output.values().keySet());
        double expectedCurrent = 1.0e-12 * Math.expm1(1.0);
        double expectedPower = THERMAL_VOLTAGE_300K * expectedCurrent;
        for (int index = 0; index < output.time().size(); index++) {
            assertEquals(THERMAL_VOLTAGE_300K, output.values().get("thermalVoltage").get(index), 1.0e-14);
            assertEquals(expectedCurrent, output.values().get("current").get(index), TOLERANCE);
            assertEquals(expectedPower, output.values().get("power").get(index), 1.0e-25);

            var reference = bound.reference(output.time().get(index)).values();
            // Check the oracle against the hand-calculated point as well as the numerical output.
            assertEquals(THERMAL_VOLTAGE_300K, reference.get("thermalVoltage"), 1.0e-14);
            assertEquals(expectedCurrent, reference.get("current"), TOLERANCE);
            assertEquals(expectedPower, reference.get("power"), 1.0e-25);
            for (String key : OUTPUT_KEYS) {
                assertEquals(output.values().get(key).get(index), reference.get(key), TOLERANCE, key);
            }
        }
    }

    @Test
    void zeroReverseAndSmallForwardBiasStayFiniteAndPreserveTheExpectedSigns() {
        DiodeCharacteristicModule module = new DiodeCharacteristicModule();
        SolverOutput zero = module.solve(module.bind(quantities(0.0, 1.0e-12, 1, 300)),
                new SimulationClock(0.1, 0.1));
        SolverOutput reverse = module.solve(module.bind(quantities(-2.0, 1.0e-12, 1, 300)),
                new SimulationClock(0.1, 0.1));
        SolverOutput smallForward = module.solve(module.bind(quantities(1.0e-15, 1.0e-12, 1, 300)),
                new SimulationClock(0.1, 0.1));

        assertEquals(0.0, zero.values().get("current").get(0), 0.0);
        assertEquals(0.0, zero.values().get("power").get(0), 0.0);
        assertEquals(-1.0e-12, reverse.values().get("current").get(0), 1.0e-25);
        assertTrue(reverse.values().get("power").get(0) > 0.0);
        assertTrue(smallForward.values().get("current").get(0) > 0.0);
        assertEquals(smallForward.values().get("current").get(0),
                module.referenceAt(module.bind(quantities(1.0e-15, 1.0e-12, 1, 300)), 0.0)
                        .values().get("current"), 1.0e-35);
    }

    @Test
    void rejectsInvalidCanonicalInputsAndUnitMismatches() {
        DiodeCharacteristicModule module = new DiodeCharacteristicModule();
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0, 0, 1, 300)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0, 1.0e-12, 0, 300)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0, 1.0e-12, 1, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new DiodeCharacteristicModule.Parameters(Double.NaN, 1.0e-12, 1, 300));

        CanonicalQuantityBag wrongCurrentUnit = new CanonicalQuantityBag(
                Map.of("voltage", BigDecimal.ZERO,
                        "saturation_current", new BigDecimal("1e-12"),
                        "ideality_factor", BigDecimal.ONE,
                        "temperature", new BigDecimal("300")),
                Map.of("voltage", "V", "saturation_current", "mA", "ideality_factor", "1", "temperature", "K"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongCurrentUnit));
    }

    @Test
    void rejectsNonFiniteDerivedValuesAndInvalidReferenceCheckpoints() {
        DiodeCharacteristicModule module = new DiodeCharacteristicModule();
        var forwardOverflow = module.bind(quantities(2, 1.0e-12, 1, 1));
        assertThrows(ArithmeticException.class,
                () -> module.solve(forwardOverflow, new SimulationClock(0.1, 0.1)));

        var thermalUnderflow = module.bind(quantities(0, 1.0e-12, 1, Double.MIN_VALUE));
        assertThrows(ArithmeticException.class,
                () -> module.solve(thermalUnderflow, new SimulationClock(0.1, 0.1)));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(module.bind(quantities(0, 1.0e-12, 1, 300)), Double.NaN));
    }

    @Test
    void allDeclaredOutputsHaveFiniteSamplesMatchingTheClock() {
        DiodeCharacteristicModule module = new DiodeCharacteristicModule();
        SolverOutput output = module.solve(module.bind(quantities(0.7, 1.0e-12, 1.5, 300)),
                new SimulationClock(1.0, 0.25));

        assertEquals(5, output.time().size());
        assertEquals(OUTPUT_KEYS, output.values().keySet());
        output.values().forEach((key, values) -> {
            assertEquals(output.time().size(), values.size(), key);
            assertTrue(values.stream().allMatch(Double::isFinite), key);
        });
    }

    private static CanonicalQuantityBag quantities(double voltage, double saturationCurrent,
                                                    double idealityFactor, double temperature) {
        return new CanonicalQuantityBag(
                Map.of("voltage", BigDecimal.valueOf(voltage),
                        "saturation_current", BigDecimal.valueOf(saturationCurrent),
                        "ideality_factor", BigDecimal.valueOf(idealityFactor),
                        "temperature", BigDecimal.valueOf(temperature)),
                Map.of("voltage", "V", "saturation_current", "A", "ideality_factor", "1", "temperature", "K"));
    }
}
