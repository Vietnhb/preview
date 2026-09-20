package com.example.backend.physics.module.circuits;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.BoundPhysicsModule;
import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdealTransformerModuleTest {
    private static final double TOLERANCE = 1.0e-12;

    @Test
    void goldenTurnsVoltageAndCurrentRatiosSatisfyPowerBalance() {
        BoundPhysicsModule module = new PhysicsModuleRegistry(java.util.List.of(new IdealTransformerModule()))
                .bind(IdealTransformerModule.NUMERICAL_SOLVER_ID,
                        IdealTransformerModule.REFERENCE_SOLVER_ID,
                        quantities(100, 25, 240, 8));

        SolverOutput output = module.solve(new SimulationClock(0.5, 0.25));
        assertEquals(java.util.List.of(0.0, 0.25, 0.5), output.time());
        assertEquals(java.util.List.of(0.25, 0.25, 0.25), output.values().get("turnsRatio"));
        assertEquals(java.util.List.of(60.0, 60.0, 60.0), output.values().get("secondaryVoltage"));
        assertEquals(java.util.List.of(2.0, 2.0, 2.0), output.values().get("primaryCurrent"));

        double primaryPower = 240.0 * 2.0;
        double secondaryPower = 60.0 * 8.0;
        assertEquals(primaryPower, secondaryPower, TOLERANCE);
        assertEquals(240.0 / 100.0, 60.0 / 25.0, TOLERANCE);
        assertEquals(8.0 / 2.0, 100.0 / 25.0, TOLERANCE);

        for (double time : output.time()) {
            var reference = module.reference(time).values();
            assertEquals(0.25, reference.get("turnsRatio"), TOLERANCE);
            assertEquals(60.0, reference.get("secondaryVoltage"), TOLERANCE);
            assertEquals(2.0, reference.get("primaryCurrent"), TOLERANCE);
            assertEquals(reference.get("secondaryVoltage") * 8.0,
                    240.0 * reference.get("primaryCurrent"), TOLERANCE);
        }
    }

    @Test
    void bindingRejectsInvalidTurnsAndNegativeMagnitudeInputs() {
        IdealTransformerModule transformer = new IdealTransformerModule();
        assertThrows(IllegalArgumentException.class, () -> transformer.bind(quantities(0, 25, 240, 8)));
        assertThrows(IllegalArgumentException.class, () -> transformer.bind(quantities(100.5, 25, 240, 8)));
        assertThrows(IllegalArgumentException.class, () -> transformer.bind(quantities(100, 25, -240, 8)));
        assertThrows(IllegalArgumentException.class, () -> transformer.bind(quantities(100, 25, 240, -8)));
    }

    @Test
    void solverRejectsFiniteInputsWhoseDerivedPowerOverflows() {
        IdealTransformerModule transformer = new IdealTransformerModule();
        var parameters = transformer.bind(quantities(1, 1, 1.0e308, 1.0e308));
        assertThrows(IllegalArgumentException.class,
                () -> transformer.solve(parameters, new SimulationClock(1, 1)));
    }

    private static CanonicalQuantityBag quantities(double primaryTurns, double secondaryTurns,
                                                   double primaryVoltage, double secondaryCurrent) {
        return new CanonicalQuantityBag(
                Map.of(
                        "primary_turns", BigDecimal.valueOf(primaryTurns),
                        "secondary_turns", BigDecimal.valueOf(secondaryTurns),
                        "primary_voltage", BigDecimal.valueOf(primaryVoltage),
                        "secondary_current", BigDecimal.valueOf(secondaryCurrent)),
                Map.of(
                        "primary_turns", "1",
                        "secondary_turns", "1",
                        "primary_voltage", "V",
                        "secondary_current", "A"));
    }
}
