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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdiabaticGasModuleTest {
    private static final double TOLERANCE = 1.0e-8;
    private final AdiabaticGasModule module = new AdiabaticGasModule();

    @Test
    void registryBindingMatchesHandCalculatedExpansionAndIndependentReference() {
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                AdiabaticGasModule.NUMERICAL_SOLVER_ID,
                AdiabaticGasModule.REFERENCE_SOLVER_ID,
                quantities(100_000.0, 0.01, 0.02, 300.0, 1.4));

        SolverOutput output = bound.solve(new SimulationClock(0.2, 0.1));
        assertEquals(List.of(0.0, 0.1, 0.2), output.time());
        assertEquals(3, output.values().get("initialPressure").size());
        assertEquals(3, output.values().get("finalPressure").size());
        assertEquals(3, output.values().get("finalTemperature").size());
        assertEquals(3, output.values().get("work").size());

        // Golden values calculated from P V^gamma, T V^(gamma-1), and the
        // adiabatic first-law work relation for this expansion.
        for (int index = 0; index < output.time().size(); index++) {
            double pressure = output.values().get("finalPressure").get(index);
            double temperature = output.values().get("finalTemperature").get(index);
            double work = output.values().get("work").get(index);
            assertEquals(37_892.91416275995, pressure, TOLERANCE);
            assertEquals(227.3574849765597, temperature, TOLERANCE);
            assertEquals(605.3542918620021, work, TOLERANCE);
            assertTrue(Double.isFinite(output.values().get("initialPressure").get(index)));
            assertTrue(Double.isFinite(pressure));
            assertTrue(Double.isFinite(temperature));
            assertTrue(Double.isFinite(work));

            var oracle = bound.reference(output.time().get(index));
            assertEquals(pressure, oracle.values().get("finalPressure"), TOLERANCE);
            assertEquals(temperature, oracle.values().get("finalTemperature"), TOLERANCE);
            assertEquals(work, oracle.values().get("work"), TOLERANCE);
        }
    }

    @Test
    void unchangedVolumeIsAZeroWorkBoundaryAndCompressionReversesWorkSign() {
        var unchanged = module.bind(quantities(80_000.0, 0.03, 0.03, 290.0, 1.4));
        SolverOutput unchangedOutput = module.solve(unchanged, new SimulationClock(0.1, 0.1));
        assertEquals(80_000.0, unchangedOutput.values().get("finalPressure").get(0), TOLERANCE);
        assertEquals(290.0, unchangedOutput.values().get("finalTemperature").get(0), TOLERANCE);
        assertEquals(0.0, unchangedOutput.values().get("work").get(0), TOLERANCE);
        assertEquals(0.0, module.referenceAt(unchanged, 0.0).values().get("work"), TOLERANCE);

        var compression = module.bind(quantities(100_000.0, 0.02, 0.01, 300.0, 1.4));
        SolverOutput compressionOutput = module.solve(compression, new SimulationClock(0.1, 0.1));
        assertTrue(compressionOutput.values().get("work").get(0) < 0.0);
        assertEquals(compressionOutput.values().get("work").get(0),
                module.referenceAt(compression, 0.0).values().get("work"), TOLERANCE);
    }

    @Test
    void binderRejectsInvalidPhysicalDomainsAndNonCanonicalUnits() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.0, 1.0, 2.0, 300.0, 1.4)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(100_000.0, 0.0, 2.0, 300.0, 1.4)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(100_000.0, 1.0, 0.0, 300.0, 1.4)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(100_000.0, 1.0, 2.0, 0.0, 1.4)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(100_000.0, 1.0, 2.0, 300.0, 1.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(100_000.0, 1.0, 2.0, 300.0, Double.POSITIVE_INFINITY)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(
                quantitiesWithPressureUnit(100_000.0, 1.0, 2.0, 300.0, 1.4, "kPa")));
    }

    @Test
    void overflowAndInvalidReferenceTimeAreRejectedBeforeEmittingNonFiniteOutput() {
        var extreme = module.bind(quantities(1.0e308, 1.0, 0.01, 300.0, 2.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(extreme, new SimulationClock(0.1, 0.1)));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(extreme, 0.0));

        var underflow = module.bind(quantities(1.0, 1.0e-300, 1.0e300, 300.0, 2.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(underflow, new SimulationClock(0.1, 0.1)));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(underflow, 0.0));

        var ordinary = module.bind(quantities(100_000.0, 1.0, 2.0, 300.0, 1.4));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(ordinary, -0.1));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(ordinary, Double.POSITIVE_INFINITY));
        assertFalse(module.solve(ordinary, new SimulationClock(0.1, 0.1)).values().isEmpty());
    }

    private static CanonicalQuantityBag quantities(double pressure, double initialVolume, double finalVolume,
                                                    double temperature, double gamma) {
        return quantitiesWithPressureUnit(pressure, initialVolume, finalVolume, temperature, gamma, "Pa");
    }

    private static CanonicalQuantityBag quantitiesWithPressureUnit(double pressure, double initialVolume,
                                                                    double finalVolume, double temperature,
                                                                    double gamma, String pressureUnit) {
        Map<String, BigDecimal> values = Map.of(
                "initial_pressure", decimal(pressure),
                "initial_volume", decimal(initialVolume),
                "final_volume", decimal(finalVolume),
                "initial_temperature", decimal(temperature),
                "heat_capacity_ratio", decimal(gamma));
        Map<String, String> units = Map.of(
                "initial_pressure", pressureUnit,
                "initial_volume", "m3",
                "final_volume", "m3",
                "initial_temperature", "K",
                "heat_capacity_ratio", "1");
        return new CanonicalQuantityBag(values, units);
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }
}
