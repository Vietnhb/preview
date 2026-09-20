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

class IdealGasIsothermalModuleTest {
    private static final double GAS_CONSTANT = 8.314462618;
    private static final double TOLERANCE = 1.0e-8;
    private final IdealGasIsothermalModule module = new IdealGasIsothermalModule();

    @Test
    void registryBindingMatchesIndependentBoyleLawAndWorkGoldenCase() {
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                IdealGasIsothermalModule.NUMERICAL_SOLVER_ID,
                IdealGasIsothermalModule.REFERENCE_SOLVER_ID,
                quantities(1.0, 300.0, 0.01, 0.002, GAS_CONSTANT));

        SolverOutput output = bound.solve(new SimulationClock(1.0, 0.5));
        assertEquals(List.of(0.0, 0.5, 1.0), output.time());
        assertEquals(Set.of("volume", "pressure", "temperature", "work"), output.values().keySet());
        assertEquals(List.of(0.01, 0.011, 0.012), output.values().get("volume"));

        // Golden values are calculated from P=nRT/V and W=nRT ln(V/V0).
        double scale = GAS_CONSTANT * 300.0;
        assertEquals(scale / 0.01, output.values().get("pressure").get(0), TOLERANCE);
        assertEquals(scale / 0.011, output.values().get("pressure").get(1), TOLERANCE);
        assertEquals(scale / 0.012, output.values().get("pressure").get(2), TOLERANCE);
        assertEquals(scale * Math.log(1.1), output.values().get("work").get(1), TOLERANCE);
        assertEquals(scale * Math.log(1.2), output.values().get("work").get(2), TOLERANCE);

        for (int index = 0; index < output.time().size(); index++) {
            assertEquals(300.0, output.values().get("temperature").get(index), TOLERANCE);
            var oracle = bound.reference(output.time().get(index)).values();
            assertEquals(output.values().get("volume").get(index), oracle.get("volume"), TOLERANCE);
            assertEquals(output.values().get("pressure").get(index), oracle.get("pressure"), TOLERANCE);
            assertEquals(output.values().get("work").get(index), oracle.get("work"), TOLERANCE);
        }
    }

    @Test
    void expansionAndCompressionHaveCorrectWorkSignAndZeroRateBoundary() {
        var expansion = module.bind(quantities(1.0, 300.0, 0.01, 0.002, GAS_CONSTANT));
        var expansionOutput = module.solve(expansion, new SimulationClock(1.0, 1.0));
        assertTrue(expansionOutput.values().get("work").get(1) > 0.0);

        var compression = module.bind(quantities(1.0, 300.0, 0.01, -0.002, GAS_CONSTANT));
        var compressionOutput = module.solve(compression, new SimulationClock(1.0, 1.0));
        assertEquals(0.008, compressionOutput.values().get("volume").get(1), TOLERANCE);
        assertTrue(compressionOutput.values().get("work").get(1) < 0.0);

        var unchanged = module.bind(quantities(1.0, 300.0, 0.01, 0.0, GAS_CONSTANT));
        var unchangedOutput = module.solve(unchanged, new SimulationClock(0.5, 0.5));
        assertEquals(List.of(0.01, 0.01), unchangedOutput.values().get("volume"));
        assertEquals(List.of(0.0, 0.0), unchangedOutput.values().get("work"));
    }

    @Test
    void rejectsInvalidDomainsMissingValuesAndNonCanonicalUnits() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(null));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.0, 300.0, 0.01, 0.0, GAS_CONSTANT)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, 0.0, 0.01, 0.0, GAS_CONSTANT)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, 300.0, 0.0, 0.0, GAS_CONSTANT)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, 300.0, 0.01, Double.NaN, GAS_CONSTANT)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, 300.0, 0.01, 0.0, 0.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(new CanonicalQuantityBag(Map.of("amount_of_substance", decimal(1.0)),
                        Map.of("amount_of_substance", "mol"))));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantitiesWithUnits("mmol", "K", "m3", "m3/s", "J/(mol*K)")));
        assertThrows(IllegalArgumentException.class,
                () -> new IdealGasIsothermalModule.Parameters(1.0, 300.0, 0.01,
                        Double.POSITIVE_INFINITY, GAS_CONSTANT));
    }

    @Test
    void rejectsZeroVolumeAndNonFiniteResultsAcrossSimulationAndReferencePaths() {
        var collapsesToZero = module.bind(quantities(1.0, 300.0, 0.01, -0.01, GAS_CONSTANT));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(collapsesToZero, new SimulationClock(1.0, 0.5)));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(collapsesToZero, 1.0));

        var invalidTimeParameters = module.bind(quantities(1.0, 300.0, 0.01, 0.0, GAS_CONSTANT));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(invalidTimeParameters, -0.1));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(invalidTimeParameters, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(invalidTimeParameters, Double.POSITIVE_INFINITY));

        var overflowingScale = module.bind(quantities(1.0e308, 1.0e308, 1.0, 0.0, 10.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(overflowingScale, new SimulationClock(0.1, 0.1)));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(overflowingScale, 0.0));
    }

    private static CanonicalQuantityBag quantities(double amount, double temperature, double initialVolume,
                                                    double volumeRate, double gasConstant) {
        return new CanonicalQuantityBag(
                Map.of("amount_of_substance", decimal(amount), "temperature", decimal(temperature),
                        "initial_volume", decimal(initialVolume), "volume_rate", decimal(volumeRate),
                        "gas_constant", decimal(gasConstant)),
                Map.of("amount_of_substance", "mol", "temperature", "K", "initial_volume", "m3",
                        "volume_rate", "m3/s", "gas_constant", "J/(mol*K)"));
    }

    private static CanonicalQuantityBag quantitiesWithUnits(String amountUnit, String temperatureUnit,
                                                              String volumeUnit, String rateUnit,
                                                              String gasConstantUnit) {
        return new CanonicalQuantityBag(
                Map.of("amount_of_substance", decimal(1.0), "temperature", decimal(300.0),
                        "initial_volume", decimal(0.01), "volume_rate", decimal(0.0),
                        "gas_constant", decimal(GAS_CONSTANT)),
                Map.of("amount_of_substance", amountUnit, "temperature", temperatureUnit,
                        "initial_volume", volumeUnit, "volume_rate", rateUnit,
                        "gas_constant", gasConstantUnit));
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }
}
