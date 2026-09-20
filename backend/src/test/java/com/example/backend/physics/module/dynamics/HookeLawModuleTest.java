package com.example.backend.physics.module.dynamics;

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

class HookeLawModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private final HookeLawModule module = new HookeLawModule();

    @Test
    void registryBindingMatchesHandCalculatedGoldenCaseAndStaticInvariant() {
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                HookeLawModule.NUMERICAL_SOLVER_ID, HookeLawModule.REFERENCE_SOLVER_ID,
                quantities(200.0, -0.05));
        SolverOutput output = bound.solve(new SimulationClock(0.2, 0.1));

        assertEquals(List.of(0.0, 0.1, 0.2), output.time());
        assertEquals(List.of(10.0, 10.0, 10.0), output.values().get("restoringForce"));
        assertEquals(List.of(0.25, 0.25, 0.25), output.values().get("elasticPotentialEnergy"));
        for (int index = 0; index < output.time().size(); index++) {
            double force = output.values().get("restoringForce").get(index);
            double energy = output.values().get("elasticPotentialEnergy").get(index);
            assertEquals(force, bound.reference(output.time().get(index)).values().get("restoringForce"), TOLERANCE);
            assertEquals(energy, bound.reference(output.time().get(index)).values().get("elasticPotentialEnergy"), TOLERANCE);
            assertEquals(-2.0 * energy, force * -0.05, TOLERANCE);
        }
    }

    @Test
    void acceptsZeroDisplacementAndRejectsNonPositiveStiffness() {
        HookeLawModule.Parameters zero = module.bind(quantities(50.0, 0.0));
        SolverOutput output = module.solve(zero, new SimulationClock(0.1, 0.1));
        assertEquals(0.0, output.values().get("restoringForce").get(0), TOLERANCE);
        assertEquals(0.0, output.values().get("elasticPotentialEnergy").get(0), TOLERANCE);
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.0, 0.1)));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(zero, -0.1));
    }

    private static CanonicalQuantityBag quantities(double stiffness, double displacement) {
        return new CanonicalQuantityBag(
                Map.of("spring_constant", BigDecimal.valueOf(stiffness),
                        "displacement", BigDecimal.valueOf(displacement)),
                Map.of("spring_constant", "N/m", "displacement", "m"));
    }
}
