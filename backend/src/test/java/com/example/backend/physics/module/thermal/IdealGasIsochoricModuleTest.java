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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdealGasIsochoricModuleTest {
    private static final double TOLERANCE = 1.0e-8;
    private final IdealGasIsochoricModule module = new IdealGasIsochoricModule();

    @Test
    void registeredBindingMatchesHandCalculatedPressureAndIndependentReference() {
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                IdealGasIsochoricModule.NUMERICAL_SOLVER_ID,
                IdealGasIsochoricModule.REFERENCE_SOLVER_ID,
                quantities(100_000.0, 0.01, 300.0, 600.0));

        SolverOutput output = bound.solve(new SimulationClock(1.0, 0.5));
        assertEquals(List.of(0.0, 0.5, 1.0), output.time());
        assertEquals(Set.of("initialPressure", "initialVolume", "initialTemperature", "finalTemperature",
                "finalPressure", "finalVolume", "work"), output.values().keySet());
        assertEquals(List.of(100_000.0, 100_000.0, 100_000.0), output.values().get("initialPressure"));
        assertEquals(List.of(0.01, 0.01, 0.01), output.values().get("initialVolume"));
        assertEquals(List.of(300.0, 300.0, 300.0), output.values().get("initialTemperature"));
        assertEquals(List.of(600.0, 600.0, 600.0), output.values().get("finalTemperature"));
        assertEquals(List.of(200_000.0, 200_000.0, 200_000.0), output.values().get("finalPressure"));
        assertEquals(List.of(0.01, 0.01, 0.01), output.values().get("finalVolume"));
        assertEquals(List.of(0.0, 0.0, 0.0), output.values().get("work"));

        var oracle = bound.reference(0.5).values();
        assertEquals(7, oracle.size());
        assertEquals(200_000.0, oracle.get("finalPressure"), TOLERANCE);
        assertEquals(0.01, oracle.get("finalVolume"), TOLERANCE);
        assertEquals(0.0, oracle.get("work"), TOLERANCE);
        assertEquals(output.values().get("finalPressure").get(1), oracle.get("finalPressure"), TOLERANCE);
    }

    @Test
    void coolingKeepsVolumeFixedAndWorkZero() {
        SolverOutput output = module.solve(module.bind(quantities(80_000.0, 0.03, 400.0, 300.0)),
                new SimulationClock(0.1, 0.1));

        assertEquals(60_000.0, output.values().get("finalPressure").get(0), TOLERANCE);
        assertEquals(0.03, output.values().get("finalVolume").get(0), TOLERANCE);
        assertEquals(0.0, output.values().get("work").get(0), TOLERANCE);
    }

    @Test
    void binderRejectsInvalidDomainsAndNonCanonicalUnits() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.0, 1.0, 300.0, 400.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(100_000.0, 0.0, 300.0, 400.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(100_000.0, 1.0, 0.0, 400.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(100_000.0, 1.0, 300.0, -1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new IdealGasIsochoricModule.Parameters(Double.POSITIVE_INFINITY, 1.0, 300.0, 400.0));
        assertThrows(IllegalArgumentException.class,
                () -> new IdealGasIsochoricModule.Parameters(100_000.0, 1.0, Double.NaN, 400.0));

        CanonicalQuantityBag wrongUnit = new CanonicalQuantityBag(
                Map.of("initial_pressure", bd(100_000), "initial_volume", bd(1),
                        "initial_temperature", bd(300), "final_temperature", bd(400)),
                Map.of("initial_pressure", "kPa", "initial_volume", "m3",
                        "initial_temperature", "K", "final_temperature", "K"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));
    }

    @Test
    void rejectsOverflowAndUnderflowInsteadOfEmittingNonFiniteOrNonPhysicalPressure() {
        var overflow = module.bind(quantities(1.0e308, 1.0, 1.0, 2.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(overflow, new SimulationClock(0.1, 0.1)));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(overflow, 0.0));

        var underflow = module.bind(quantities(1.0e-300, 1.0, 1.0e300, 1.0e-300));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(underflow, new SimulationClock(0.1, 0.1)));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(underflow, 0.0));

        SolverOutput ordinary = module.solve(module.bind(quantities(100_000.0, 0.02, 280.0, 350.0)),
                new SimulationClock(0.2, 0.1));
        ordinary.values().values().forEach(series -> series.forEach(value -> assertTrue(Double.isFinite(value))));
    }

    @Test
    void referenceRejectsInvalidCheckpointTime() {
        var parameters = module.bind(quantities(100_000.0, 1.0, 300.0, 400.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(parameters, Double.POSITIVE_INFINITY));
    }

    private static CanonicalQuantityBag quantities(double pressure, double volume,
                                                    double initialTemperature, double finalTemperature) {
        return new CanonicalQuantityBag(
                Map.of("initial_pressure", bd(pressure), "initial_volume", bd(volume),
                        "initial_temperature", bd(initialTemperature), "final_temperature", bd(finalTemperature)),
                Map.of("initial_pressure", "Pa", "initial_volume", "m3",
                        "initial_temperature", "K", "final_temperature", "K"));
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
