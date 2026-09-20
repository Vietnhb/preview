package com.example.backend.physics.module.circuits;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BasicCircuitModulesTest {
    private static final double TOLERANCE = 1.0e-12;

    @Test
    void ohmsLawMatchesHandCalculatedValuesAndSamplesEveryRequestedTime() {
        OhmsLawModule module = new OhmsLawModule();
        OhmsLawModule.Parameters parameters = module.bind(new CanonicalQuantityBag(
                Map.of("voltage", new BigDecimal("12"), "resistance", new BigDecimal("4")),
                Map.of("voltage", "V", "resistance", "ohm")));

        SolverOutput numerical = module.solve(parameters, new SimulationClock(1.0, 0.25));
        assertEquals(java.util.List.of(0.0, 0.25, 0.5, 0.75, 1.0), numerical.time());
        assertEquals(Set.of("voltage", "resistance", "current", "power"), numerical.values().keySet());
        for (int index = 0; index < numerical.time().size(); index++) {
            assertEquals(12.0, numerical.values().get("voltage").get(index), TOLERANCE);
            assertEquals(4.0, numerical.values().get("resistance").get(index), TOLERANCE);
            assertEquals(3.0, numerical.values().get("current").get(index), TOLERANCE);
            assertEquals(36.0, numerical.values().get("power").get(index), TOLERANCE);
            assertOhmsLawOracle(module.referenceAt(parameters, numerical.time().get(index)));
        }
    }

    @Test
    void capacitorMatchesHandCalculatedValuesAndIndependentOracle() {
        CapacitorBasicModule module = new CapacitorBasicModule();
        CapacitorBasicModule.Parameters parameters = module.bind(new CanonicalQuantityBag(
                Map.of("capacitance", new BigDecimal("0.5"), "voltage", new BigDecimal("4")),
                Map.of("capacitance", "F", "voltage", "V")));

        SolverOutput numerical = module.solve(parameters, new SimulationClock(0.4, 0.2));
        assertEquals(java.util.List.of(0.0, 0.2, 0.4), numerical.time());
        assertEquals(Set.of("charge", "energy", "voltage"), numerical.values().keySet());
        for (int index = 0; index < numerical.time().size(); index++) {
            assertEquals(2.0, numerical.values().get("charge").get(index), TOLERANCE);
            assertEquals(4.0, numerical.values().get("energy").get(index), TOLERANCE);
            assertEquals(4.0, numerical.values().get("voltage").get(index), TOLERANCE);
            AnalyticalPoint oracle = module.referenceAt(parameters, numerical.time().get(index));
            assertEquals(numerical.values().get("charge").get(index), oracle.values().get("charge"), TOLERANCE);
            assertEquals(numerical.values().get("energy").get(index), oracle.values().get("energy"), TOLERANCE);
            assertEquals(numerical.values().get("voltage").get(index), oracle.values().get("voltage"), TOLERANCE);
        }
    }

    @Test
    void modulesRejectMissingAndInvalidPhysicalInputs() {
        OhmsLawModule ohmsLaw = new OhmsLawModule();
        CapacitorBasicModule capacitor = new CapacitorBasicModule();

        assertThrows(IllegalArgumentException.class, () -> ohmsLaw.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class, () -> ohmsLaw.bind(new CanonicalQuantityBag(
                Map.of("voltage", BigDecimal.ONE, "resistance", BigDecimal.ZERO),
                Map.of("voltage", "V", "resistance", "ohm"))));
        assertThrows(IllegalArgumentException.class, () -> new OhmsLawModule.Parameters(1, Double.NaN));

        assertThrows(IllegalArgumentException.class, () -> capacitor.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class, () -> capacitor.bind(new CanonicalQuantityBag(
                Map.of("capacitance", BigDecimal.ZERO, "voltage", BigDecimal.ONE),
                Map.of("capacitance", "F", "voltage", "V"))));
        assertThrows(IllegalArgumentException.class, () -> new CapacitorBasicModule.Parameters(1, Double.POSITIVE_INFINITY));
    }

    @Test
    void modulesRejectOverflowingOutputsAndInvalidReferenceTimes() {
        OhmsLawModule ohmsLaw = new OhmsLawModule();
        OhmsLawModule.Parameters extremeOhms = new OhmsLawModule.Parameters(Double.MAX_VALUE, Double.MIN_VALUE);
        assertThrows(ArithmeticException.class, () -> ohmsLaw.solve(extremeOhms, new SimulationClock(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> ohmsLaw.referenceAt(
                new OhmsLawModule.Parameters(1, 1), Double.NaN));

        CapacitorBasicModule capacitor = new CapacitorBasicModule();
        CapacitorBasicModule.Parameters extremeCapacitor = new CapacitorBasicModule.Parameters(
                Double.MAX_VALUE, Double.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> capacitor.solve(extremeCapacitor, new SimulationClock(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> capacitor.referenceAt(
                new CapacitorBasicModule.Parameters(1, 1), -1));
    }

    private static void assertOhmsLawOracle(AnalyticalPoint oracle) {
        assertEquals(12.0, oracle.values().get("voltage"), TOLERANCE);
        assertEquals(4.0, oracle.values().get("resistance"), TOLERANCE);
        assertEquals(3.0, oracle.values().get("current"), TOLERANCE);
        assertEquals(36.0, oracle.values().get("power"), TOLERANCE);
    }
}
