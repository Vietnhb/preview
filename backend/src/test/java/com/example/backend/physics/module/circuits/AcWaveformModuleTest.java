package com.example.backend.physics.module.circuits;

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

class AcWaveformModuleTest {
    @Test
    void numericalAndIndependentReferenceAgreeWithHandCalculatedGoldenPoints() {
        CanonicalQuantityBag quantities = new CanonicalQuantityBag(
                Map.of("peak_voltage", new BigDecimal("10"), "frequency", new BigDecimal("50")),
                Map.of("peak_voltage", "V", "frequency", "Hz"));
        PhysicsModuleRegistry registry = new PhysicsModuleRegistry(List.of(new AcWaveformModule()));
        BoundPhysicsModule bound = registry.bind(
                AcWaveformModule.NUMERICAL_SOLVER_ID,
                AcWaveformModule.REFERENCE_SOLVER_ID,
                quantities);

        SolverOutput output = bound.solve(new SimulationClock(0.01, 0.0025));
        assertEquals(List.of(0.0, 0.0025, 0.005, 0.0075, 0.01), output.time());
        assertEquals(0.0, output.values().get("voltage").get(0), 1e-12);
        assertEquals(10.0, output.values().get("voltage").get(2), 1e-12);
        assertEquals(0.0, output.values().get("voltage").get(4), 1e-12);
        assertEquals(10.0 / Math.sqrt(2.0), output.values().get("rmsVoltage").get(2), 1e-12);

        for (int i = 0; i < output.time().size(); i++) {
            double expected = output.values().get("voltage").get(i);
            assertEquals(expected, bound.reference(output.time().get(i)).values().get("voltage"), 1e-12);
        }
    }

    @Test
    void rejectsInvalidSimulationClockAndReferenceTime() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationClock(1, 0));
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(new AcWaveformModule()))
                .bindModule(AcWaveformModule.MODULE_ID, new CanonicalQuantityBag(
                        Map.of("peak_voltage", BigDecimal.ONE, "frequency", BigDecimal.ONE),
                        Map.of("peak_voltage", "V", "frequency", "Hz")));
        assertThrows(IllegalArgumentException.class, () -> bound.reference(Double.NaN));
    }
}
