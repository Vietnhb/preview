package com.example.backend.physics.module.modern;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtomicSpectraModuleTest {
    private final AtomicSpectraModule module = new AtomicSpectraModule();

    @Test
    void hydrogenBalmerAlphaGoldenMatchesIndependentEnergyLevelReference() {
        var parameters = module.bind(levels(3.0, 2.0));
        var output = module.solve(parameters, new SimulationClock(0.2, 0.1));
        assertEquals(3, output.time().size());
        assertEquals(6.561122764193189e-7, output.values().get("wavelength").get(0), 1.0e-21);
        assertEquals(4.56922494479289e14, output.values().get("frequency").get(0), 1.0);
        assertEquals(3.0276005015327565e-19, output.values().get("photonEnergy").get(0), 1.0e-33);

        var reference = module.referenceAt(parameters, 0.0).values();
        output.values().forEach((key, series) -> assertEquals(series.get(0), reference.get(key),
                Math.abs(series.get(0)) * 1.0e-14, key));
    }

    @Test
    void lowestAllowedTransitionAndBoundedHighLevelTransitionStayFinite() {
        var lowest = module.bind(levels(2.0, 1.0));
        var lowestOutput = module.solve(lowest, new SimulationClock(0.1, 0.1));
        assertEquals(1.0 / (10_973_731.568160 * (1.0 - 1.0 / 4.0)),
                lowestOutput.values().get("wavelength").get(0), 1.0e-19);

        var high = module.bind(levels(1000.0, 999.0));
        var highOutput = module.solve(high, new SimulationClock(0.1, 0.1));
        assertTrue(Double.isFinite(highOutput.values().get("wavelength").get(0)));
        assertTrue(Double.isFinite(highOutput.values().get("photonEnergy").get(0)));
    }

    @Test
    void rejectsInvalidLevelOrderingFractionalValuesUnitsAndReferenceTimes() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class, () -> module.bind(levels(2.0, 2.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(levels(2.0, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(levels(3.5, 2.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(levels(1001.0, 1.0)));
        var valid = module.bind(levels(3.0, 2.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, -0.1));
    }

    private static CanonicalQuantityBag levels(double initial, double terminal) {
        return new CanonicalQuantityBag(
                Map.of("initial_level", BigDecimal.valueOf(initial),
                        "final_level", BigDecimal.valueOf(terminal)),
                Map.of("initial_level", "1", "final_level", "1"));
    }
}
