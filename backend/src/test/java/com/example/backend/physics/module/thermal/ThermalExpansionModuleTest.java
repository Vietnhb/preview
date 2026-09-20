package com.example.backend.physics.module.thermal;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.BoundPhysicsModule;
import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThermalExpansionModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private final ThermalExpansionModule module = new ThermalExpansionModule();

    @Test
    void registeredBindingMatchesHandCalculatedHeatingGoldenAndIndependentReference() {
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                ThermalExpansionModule.NUMERICAL_SOLVER_ID,
                ThermalExpansionModule.REFERENCE_SOLVER_ID,
                quantities(2.0, 1.0e-5, 300.0, 400.0));

        SolverOutput output = bound.solve(new SimulationClock(1.0, 0.5));
        assertEquals(List.of(0.0, 0.5, 1.0), output.time());
        assertEquals(List.of(100.0, 100.0, 100.0), output.values().get("deltaTemperature"));
        assertEquals(List.of(0.002, 0.002, 0.002), output.values().get("extension"));
        assertEquals(List.of(2.002, 2.002, 2.002), output.values().get("finalLength"));

        var oracle = bound.reference(0.5).values();
        assertEquals(100.0, oracle.get("deltaTemperature"), TOLERANCE);
        assertEquals(0.002, oracle.get("extension"), TOLERANCE);
        assertEquals(2.002, oracle.get("finalLength"), TOLERANCE);
    }

    @Test
    void coolingAndZeroCoefficientRespectPhysicalBoundaries() {
        SolverOutput cooling = module.solve(module.bind(quantities(2.0, 1.0e-5, 400.0, 300.0)),
                new SimulationClock(0.1, 0.1));
        assertEquals(-100.0, cooling.values().get("deltaTemperature").get(0), TOLERANCE);
        assertEquals(-0.002, cooling.values().get("extension").get(0), TOLERANCE);
        assertEquals(1.998, cooling.values().get("finalLength").get(0), TOLERANCE);

        SolverOutput noExpansion = module.solve(module.bind(quantities(2.0, 0.0, 300.0, 400.0)),
                new SimulationClock(0.1, 0.1));
        assertEquals(0.0, noExpansion.values().get("extension").get(0), TOLERANCE);
        assertEquals(2.0, noExpansion.values().get("finalLength").get(0), TOLERANCE);

        assertThrows(IllegalArgumentException.class,
                () -> module.solve(module.bind(quantities(1.0, 1.0, 2.0, 1.0)),
                        new SimulationClock(0.1, 0.1)));
    }

    @Test
    void rejectsInvalidDomainAndIncorrectCanonicalUnits() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.0, 1.0e-5, 300.0, 400.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(2.0, -1.0e-5, 300.0, 400.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(2.0, 1.0e-5, 0.0, 400.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(2.0, 1.0e-5, 300.0, -1.0)));

        CanonicalQuantityBag wrongUnit = new CanonicalQuantityBag(
                Map.of("initial_length", bd(2), "linear_expansion_coefficient", bd(1.0e-5),
                        "initial_temperature", bd(300), "final_temperature", bd(400)),
                Map.of("initial_length", "m", "linear_expansion_coefficient", "1/C",
                        "initial_temperature", "K", "final_temperature", "K"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));
    }

    @Test
    void rejectsOverflowAndKeepsAllProducedValuesFinite() {
        var extreme = module.bind(quantities(1.0e308, 2.0, 1.0, 2.0));
        assertThrows(ArithmeticException.class, () -> module.solve(extreme, new SimulationClock(0.1, 0.1)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(extreme, 0.0));

        SolverOutput ordinary = module.solve(module.bind(quantities(3.0, 2.0e-5, 250.0, 500.0)),
                new SimulationClock(0.2, 0.1));
        ordinary.values().values().forEach(series -> series.forEach(value -> assertTrue(Double.isFinite(value))));
    }

    @Test
    void referenceRejectsInvalidCheckpointTime() {
        var parameters = module.bind(quantities(2.0, 1.0e-5, 300.0, 400.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, Double.NaN));
    }

    private static CanonicalQuantityBag quantities(double length, double coefficient, double initialTemperature,
                                                   double finalTemperature) {
        return new CanonicalQuantityBag(
                Map.of("initial_length", bd(length), "linear_expansion_coefficient", bd(coefficient),
                        "initial_temperature", bd(initialTemperature), "final_temperature", bd(finalTemperature)),
                Map.of("initial_length", "m", "linear_expansion_coefficient", "1/K",
                        "initial_temperature", "K", "final_temperature", "K"));
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
