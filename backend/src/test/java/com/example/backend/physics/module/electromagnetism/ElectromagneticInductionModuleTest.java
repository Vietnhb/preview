package com.example.backend.physics.module.electromagnetism;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElectromagneticInductionModuleTest {
    private static final double TOLERANCE = 1.0e-12;

    @Test
    void handCalculatedFaradayCoilCaseMatchesIndependentFluxAndEmfOracle() {
        var module = new ElectromagneticInductionModule();
        var bound = new PhysicsModuleRegistry(List.of(module)).bind(
                ElectromagneticInductionModule.NUMERICAL_SOLVER_ID,
                ElectromagneticInductionModule.REFERENCE_SOLVER_ID,
                quantities(100.0, 0.2, 0.01, 0.5, 0.0));
        var output = bound.solve(new SimulationClock(0.2, 0.1));
        assertEquals(List.of(0.0, 0.1, 0.2), output.time());
        assertEquals(List.of(0.2, 0.25, 0.30000000000000004), output.values().get("magneticField"));
        assertEquals(0.003, output.values().get("magneticFlux").get(2), TOLERANCE);
        assertEquals(-0.5, output.scalarOutputs().get("inducedEmf"), TOLERANCE);
        var oracle = bound.reference(0.2).values();
        assertEquals(output.values().get("magneticField").get(2), oracle.get("magneticField"), TOLERANCE);
        assertEquals(output.values().get("magneticFlux").get(2), oracle.get("magneticFlux"), TOLERANCE);
        assertEquals(output.scalarOutputs().get("inducedEmf"), oracle.get("inducedEmf"), TOLERANCE);
    }

    @Test
    void coilParallelToFieldHasZeroFluxAndInducedEmfAtTheBoundary() {
        var module = new ElectromagneticInductionModule();
        var p = module.bind(quantities(1.0, 2.0, 3.0, -4.0, Math.PI / 2.0));
        var output = module.solve(p, new SimulationClock(0.1, 0.1));
        assertEquals(0.0, output.values().get("magneticFlux").get(0), TOLERANCE);
        assertEquals(0.0, output.scalarOutputs().get("inducedEmf"), TOLERANCE);
        assertTrue(output.values().get("magneticField").get(1) < output.values().get("magneticField").get(0));
    }

    @Test
    void inductionRejectsNonintegerTurnsOutOfRangeAngleAndWrongCanonicalUnit() {
        var module = new ElectromagneticInductionModule();
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.5, 0.2, 0.01, 1.0, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(2.0, 0.2, 0.01, 1.0, Math.PI + 0.01)));
        CanonicalQuantityBag wrongUnit = new CanonicalQuantityBag(
                Map.of("turns", BigDecimal.ONE, "magnetic_field", BigDecimal.ONE, "coil_area", BigDecimal.ONE,
                        "magnetic_field_rate", BigDecimal.ONE, "coil_angle", BigDecimal.ZERO),
                Map.of("turns", "1", "magnetic_field", "T", "coil_area", "m", "magnetic_field_rate", "T/s",
                        "coil_angle", "rad"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));
    }

    private static CanonicalQuantityBag quantities(double turns, double field, double area, double rate, double angle) {
        return new CanonicalQuantityBag(Map.of("turns", BigDecimal.valueOf(turns),
                        "magnetic_field", BigDecimal.valueOf(field), "coil_area", BigDecimal.valueOf(area),
                        "magnetic_field_rate", BigDecimal.valueOf(rate), "coil_angle", BigDecimal.valueOf(angle)),
                Map.of("turns", "1", "magnetic_field", "T", "coil_area", "m2",
                        "magnetic_field_rate", "T/s", "coil_angle", "rad"));
    }
}
