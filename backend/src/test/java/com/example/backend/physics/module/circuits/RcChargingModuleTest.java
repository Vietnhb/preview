package com.example.backend.physics.module.circuits;

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

class RcChargingModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private static final Set<String> OUTPUT_KEYS = Set.of("voltage", "current");

    @Test
    void bindsCanonicalQuantitiesAndMatchesHandCalculatedChargingValues() {
        RcChargingModule module = new RcChargingModule();
        var parameters = module.bind(quantities(10.0, 1000.0, 0.001));
        SolverOutput output = module.solve(parameters, new SimulationClock(1.0, 0.5));

        assertEquals(RcChargingModule.MODULE_ID, module.moduleId());
        assertEquals("rc_charging_solver", module.numericalSolverId());
        assertEquals("rc_charging_reference", module.referenceSolverId());
        assertEquals(java.util.List.of(0.0, 0.5, 1.0), output.time());
        assertEquals(OUTPUT_KEYS, output.values().keySet());
        assertEquals(Map.of(), output.positions());
        assertEquals(Map.of(), output.velocities());
        assertEquals(Map.of(), output.accelerations());

        double expectedVoltageAtOneTau = 6.321205588285577;
        double expectedCurrentAtZero = 0.01;
        double expectedCurrentAtOneTau = 0.0036787944117144233;
        assertEquals(0.0, output.values().get("voltage").get(0), TOLERANCE);
        assertEquals(expectedCurrentAtZero, output.values().get("current").get(0), TOLERANCE);
        assertEquals(expectedVoltageAtOneTau, output.values().get("voltage").get(2), TOLERANCE);
        assertEquals(expectedCurrentAtOneTau, output.values().get("current").get(2), TOLERANCE);

        var referenceAtOneTau = module.referenceAt(parameters, 1.0).values();
        assertEquals(expectedVoltageAtOneTau, referenceAtOneTau.get("voltage"), TOLERANCE);
        assertEquals(expectedCurrentAtOneTau, referenceAtOneTau.get("current"), TOLERANCE);
    }

    @Test
    void signedSourceVoltageKeepsItsSignAndEveryOutputSeriesMatchesTheTimeline() {
        RcChargingModule module = new RcChargingModule();
        var parameters = module.bind(quantities(-10.0, 1000.0, 0.001));
        SolverOutput output = module.solve(parameters, new SimulationClock(1.0, 0.25));

        assertEquals(5, output.time().size());
        assertEquals(OUTPUT_KEYS, output.values().keySet());
        output.values().forEach((key, values) -> {
            assertEquals(output.time().size(), values.size(), key);
            assertTrue(values.stream().allMatch(Double::isFinite), key);
        });
        assertEquals(-6.321205588285577, output.values().get("voltage").getLast(), TOLERANCE);
        assertEquals(-0.0036787944117144233, output.values().get("current").getLast(), TOLERANCE);
    }

    @Test
    void referenceUsesStableExpm1AtVerySmallTimes() {
        RcChargingModule module = new RcChargingModule();
        var parameters = new RcChargingModule.Parameters(1.0, 1.0, 1.0);

        SolverOutput numerical = module.solve(parameters, new SimulationClock(1.0e-20, 1.0e-20));
        double referenceVoltage = module.referenceAt(parameters, 1.0e-20).values().get("voltage");

        assertEquals(0.0, numerical.values().get("voltage").getLast(), 0.0);
        assertEquals(1.0e-20, referenceVoltage, 1.0e-35);
    }

    @Test
    void acceptsOnlyNormalizedCanonicalUnitsAndRequiresAllCanonicalKeys() {
        RcChargingModule module = new RcChargingModule();
        assertEquals(1000.0, module.bind(quantities(10, 1000, 0.001)).resistance(), TOLERANCE);

        CanonicalQuantityBag symbolOhm = new CanonicalQuantityBag(
                Map.of("voltage", BigDecimal.TEN, "resistance", BigDecimal.valueOf(1000),
                        "capacitance", new BigDecimal("0.001")),
                Map.of("voltage", "V", "resistance", "\u03A9", "capacitance", "F"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(symbolOhm));
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
    }

    @Test
    void rejectsInvalidResistanceCapacitanceAndUnrepresentableTimeConstants() {
        RcChargingModule module = new RcChargingModule();
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1, 0, 1)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1, 1, -1)));
        assertThrows(IllegalArgumentException.class,
                () -> new RcChargingModule.Parameters(Double.NaN, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new RcChargingModule.Parameters(1, Double.POSITIVE_INFINITY, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new RcChargingModule.Parameters(1, Double.MAX_VALUE, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new RcChargingModule.Parameters(1, Double.MIN_VALUE, 0.5));
    }

    @Test
    void rejectsNonFiniteInitialCurrentAndInvalidReferenceTimes() {
        RcChargingModule module = new RcChargingModule();
        var overflowingCurrent = new RcChargingModule.Parameters(Double.MAX_VALUE, Double.MIN_VALUE, 1.0);
        assertThrows(ArithmeticException.class,
                () -> module.solve(overflowingCurrent, new SimulationClock(1.0, 1.0)));

        var normal = new RcChargingModule.Parameters(10.0, 1000.0, 0.001);
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(normal, -1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(normal, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(normal, Double.POSITIVE_INFINITY));
    }

    @Test
    void exponentialUnderflowConvergesToTheFiniteSteadyState() {
        RcChargingModule module = new RcChargingModule();
        SolverOutput output = module.solve(new RcChargingModule.Parameters(10.0, 1.0, 1.0),
                new SimulationClock(1000.0, 1000.0));

        assertEquals(10.0, output.values().get("voltage").getLast(), TOLERANCE);
        assertEquals(0.0, output.values().get("current").getLast(), 0.0);
        assertTrue(output.values().values().stream()
                .flatMap(java.util.Collection::stream).allMatch(Double::isFinite));
    }

    private static CanonicalQuantityBag quantities(double voltage, double resistance, double capacitance) {
        return new CanonicalQuantityBag(
                Map.of("voltage", BigDecimal.valueOf(voltage),
                        "resistance", BigDecimal.valueOf(resistance),
                        "capacitance", BigDecimal.valueOf(capacitance)),
                Map.of("voltage", "V", "resistance", "ohm", "capacitance", "F"));
    }
}
