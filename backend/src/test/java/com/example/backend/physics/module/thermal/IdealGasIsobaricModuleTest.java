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

class IdealGasIsobaricModuleTest {
    private static final double TOLERANCE = 1.0e-8;
    private final IdealGasIsobaricModule module = new IdealGasIsobaricModule();

    @Test
    void registeredBindingMatchesHandCalculatedExpansionAndIndependentReference() {
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                IdealGasIsobaricModule.NUMERICAL_SOLVER_ID,
                IdealGasIsobaricModule.REFERENCE_SOLVER_ID,
                quantities(100_000.0, 1.0, 300.0, 600.0));

        SolverOutput output = bound.solve(new SimulationClock(1.0, 0.5));
        assertEquals(List.of(0.0, 0.5, 1.0), output.time());
        assertEquals(Set.of("initialPressure", "initialVolume", "initialTemperature", "finalTemperature",
                "finalPressure", "finalVolume", "work"), output.values().keySet());
        assertEquals(List.of(100_000.0, 100_000.0, 100_000.0), output.values().get("initialPressure"));
        assertEquals(List.of(1.0, 1.0, 1.0), output.values().get("initialVolume"));
        assertEquals(List.of(300.0, 300.0, 300.0), output.values().get("initialTemperature"));
        assertEquals(List.of(600.0, 600.0, 600.0), output.values().get("finalTemperature"));
        assertEquals(List.of(100_000.0, 100_000.0, 100_000.0), output.values().get("finalPressure"));
        assertEquals(List.of(2.0, 2.0, 2.0), output.values().get("finalVolume"));
        assertEquals(List.of(100_000.0, 100_000.0, 100_000.0), output.values().get("work"));

        // Golden values use Charles' law and pressure-volume work for a
        // hand-checkable doubling of absolute temperature at fixed pressure.
        for (int index = 0; index < output.time().size(); index++) {
            var oracle = bound.reference(output.time().get(index)).values();
            assertEquals(output.values().get("finalPressure").get(index), oracle.get("finalPressure"), TOLERANCE);
            assertEquals(output.values().get("finalVolume").get(index), oracle.get("finalVolume"), TOLERANCE);
            assertEquals(output.values().get("work").get(index), oracle.get("work"), TOLERANCE);
        }
    }

    @Test
    void coolingProducesNegativeWorkAndUnchangedTemperatureProducesZeroWork() {
        SolverOutput cooling = module.solve(module.bind(quantities(80_000.0, 0.03, 400.0, 300.0)),
                new SimulationClock(0.1, 0.1));
        assertEquals(0.0225, cooling.values().get("finalVolume").get(0), TOLERANCE);
        assertEquals(-600.0, cooling.values().get("work").get(0), TOLERANCE);
        assertTrue(cooling.values().get("work").get(0) < 0.0);

        SolverOutput unchanged = module.solve(module.bind(quantities(80_000.0, 0.03, 400.0, 400.0)),
                new SimulationClock(0.1, 0.1));
        assertEquals(0.03, unchanged.values().get("finalVolume").get(0), TOLERANCE);
        assertEquals(0.0, unchanged.values().get("work").get(0), TOLERANCE);
        assertEquals(0.0, module.referenceAt(module.bind(quantities(80_000.0, 0.03, 400.0, 400.0)), 0.0)
                .values().get("work"), TOLERANCE);
    }

    @Test
    void binderAndReferenceRejectInvalidDomainsAndNonCanonicalUnits() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(null));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.0, 1.0, 300.0, 400.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(100_000.0, 0.0, 300.0, 400.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(100_000.0, 1.0, 0.0, 400.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(100_000.0, 1.0, 300.0, -1.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(
                quantitiesWithUnits("kPa", "m3", "K", "K")));
        assertThrows(IllegalArgumentException.class, () -> module.bind(
                new CanonicalQuantityBag(Map.of("initial_pressure", bd(100_000.0)),
                        Map.of("initial_pressure", "Pa"))));

        var parameters = module.bind(quantities(100_000.0, 1.0, 300.0, 400.0));
        assertThrows(IllegalArgumentException.class, () -> module.solve(parameters, null));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(parameters, Double.POSITIVE_INFINITY));
    }

    @Test
    void overflowAndUnderflowAreRejectedRatherThanSerializedAsInvalidPhysics() {
        var overflow = module.bind(quantities(1.0e308, 1.0e308, 1.0, 2.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(overflow, new SimulationClock(0.1, 0.1)));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(overflow, 0.0));

        var underflow = module.bind(quantities(1.0, 1.0e-300, 1.0e300, 1.0e-300));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(underflow, new SimulationClock(0.1, 0.1)));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(underflow, 0.0));
    }

    private static CanonicalQuantityBag quantities(double pressure, double volume,
                                                   double initialTemperature, double finalTemperature) {
        return quantitiesWithUnits("Pa", "m3", "K", "K", pressure, volume, initialTemperature, finalTemperature);
    }

    private static CanonicalQuantityBag quantitiesWithUnits(String pressureUnit, String volumeUnit,
                                                             String initialTemperatureUnit, String finalTemperatureUnit) {
        return quantitiesWithUnits(pressureUnit, volumeUnit, initialTemperatureUnit,
                finalTemperatureUnit, 100_000.0, 1.0, 300.0, 400.0);
    }

    private static CanonicalQuantityBag quantitiesWithUnits(String pressureUnit, String volumeUnit,
                                                             String initialTemperatureUnit, String finalTemperatureUnit,
                                                             double pressure, double volume,
                                                             double initialTemperature, double finalTemperature) {
        return new CanonicalQuantityBag(
                Map.of("initial_pressure", bd(pressure), "initial_volume", bd(volume),
                        "initial_temperature", bd(initialTemperature), "final_temperature", bd(finalTemperature)),
                Map.of("initial_pressure", pressureUnit, "initial_volume", volumeUnit,
                        "initial_temperature", initialTemperatureUnit, "final_temperature", finalTemperatureUnit));
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
