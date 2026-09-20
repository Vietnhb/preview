package com.example.backend.physics.module.circuits;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RcDischargingModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private static final Set<String> OUTPUT_KEYS = Set.of("voltage", "current");

    @Test
    void bindsCanonicalInputsAndMatchesIndependentHandCalculatedCheckpoints() {
        RcDischargingModule module = new RcDischargingModule();
        RcDischargingModule.Parameters parameters = module.bind(quantities(10.0, 1000.0, 0.001));
        SolverOutput output = module.solve(parameters, new SimulationClock(2.0, 1.0));

        assertEquals("rc_discharging", module.moduleId());
        assertEquals("rc_discharging_solver", module.numericalSolverId());
        assertEquals("rc_discharging_reference", module.referenceSolverId());
        assertEquals(List.of(0.0, 1.0, 2.0), output.time());
        assertEquals(OUTPUT_KEYS, output.values().keySet());
        assertEquals(Map.of(), output.positions());
        assertEquals(Map.of(), output.velocities());
        assertEquals(Map.of(), output.accelerations());

        double voltageAtOneTau = 3.6787944117144233;
        double currentAtZero = -0.01;
        double currentAtOneTau = -0.0036787944117144233;
        assertEquals(10.0, output.values().get("voltage").getFirst(), TOLERANCE);
        assertEquals(currentAtZero, output.values().get("current").getFirst(), TOLERANCE);
        assertEquals(voltageAtOneTau, output.values().get("voltage").get(1), TOLERANCE);
        assertEquals(currentAtOneTau, output.values().get("current").get(1), TOLERANCE);

        var oracleAtOneTau = module.referenceAt(parameters, 1.0).values();
        assertEquals(voltageAtOneTau, oracleAtOneTau.get("voltage"), TOLERANCE);
        assertEquals(currentAtOneTau, oracleAtOneTau.get("current"), TOLERANCE);
    }

    @Test
    void keepsSignedInitialVoltageAndMaintainsOutputSeriesShape() {
        RcDischargingModule module = new RcDischargingModule();
        SolverOutput output = module.solve(module.bind(quantities(-10.0, 1000.0, 0.001)),
                new SimulationClock(1.0, 0.25));

        assertEquals(5, output.time().size());
        assertEquals(OUTPUT_KEYS, output.values().keySet());
        output.values().forEach((key, values) -> {
            assertEquals(output.time().size(), values.size(), key);
            assertTrue(values.stream().allMatch(Double::isFinite), key);
        });
        assertEquals(-3.6787944117144233, output.values().get("voltage").getLast(), TOLERANCE);
        assertEquals(0.0036787944117144233, output.values().get("current").getLast(), TOLERANCE);
    }

    @Test
    void referenceRecomputesCurrentFromTheCheckpointVoltageAndResistorLaw() {
        RcDischargingModule module = new RcDischargingModule();
        var parameters = new RcDischargingModule.Parameters(12.0, 2000.0, 0.0005);

        var oracle = module.referenceAt(parameters, 2.0).values();

        assertEquals(12.0 / (Math.E * Math.E), oracle.get("voltage"), TOLERANCE);
        assertEquals(-12.0 / (2000.0 * Math.E * Math.E), oracle.get("current"), TOLERANCE);
    }

    @Test
    void acceptsOnlyNormalizedUnitsAndRequiresAllCanonicalQuantities() {
        RcDischargingModule module = new RcDischargingModule();
        assertEquals(1000.0, module.bind(quantities(10.0, 1000.0, 0.001)).resistance(), TOLERANCE);

        CanonicalQuantityBag symbolOhm = new CanonicalQuantityBag(
                Map.of("voltage", BigDecimal.TEN, "resistance", BigDecimal.valueOf(1000),
                        "capacitance", new BigDecimal("0.001")),
                Map.of("voltage", "V", "resistance", "Ω", "capacitance", "F"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(symbolOhm));
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
    }

    @Test
    void rejectsInvalidParametersAndReferenceTimes() {
        RcDischargingModule module = new RcDischargingModule();
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, 0.0, 1.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, 1.0, -1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new RcDischargingModule.Parameters(Double.NaN, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> new RcDischargingModule.Parameters(1.0, Double.MAX_VALUE, 2.0));
        assertThrows(IllegalArgumentException.class,
                () -> new RcDischargingModule.Parameters(1.0, Double.MIN_VALUE, 0.5));

        var normal = new RcDischargingModule.Parameters(10.0, 1000.0, 0.001);
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(normal, -1.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(normal, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(normal, Double.POSITIVE_INFINITY));
    }

    @Test
    void handlesExponentialUnderflowAndRejectsOverflowingInitialCurrent() {
        RcDischargingModule module = new RcDischargingModule();
        SolverOutput output = module.solve(new RcDischargingModule.Parameters(10.0, 1.0, 1.0),
                new SimulationClock(1000.0, 1000.0));
        assertEquals(0.0, output.values().get("voltage").getLast(), 0.0);
        assertEquals(0.0, output.values().get("current").getLast(), 0.0);

        var overflowingCurrent = new RcDischargingModule.Parameters(Double.MAX_VALUE, Double.MIN_VALUE, 1.0);
        assertThrows(ArithmeticException.class,
                () -> module.solve(overflowingCurrent, new SimulationClock(1.0, 1.0)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(overflowingCurrent, 0.0));
    }

    private static CanonicalQuantityBag quantities(double voltage, double resistance, double capacitance) {
        return new CanonicalQuantityBag(
                Map.of("voltage", BigDecimal.valueOf(voltage),
                        "resistance", BigDecimal.valueOf(resistance),
                        "capacitance", BigDecimal.valueOf(capacitance)),
                Map.of("voltage", "V", "resistance", "ohm", "capacitance", "F"));
    }
}
