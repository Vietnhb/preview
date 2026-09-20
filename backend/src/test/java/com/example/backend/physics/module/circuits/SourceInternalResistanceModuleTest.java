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

class SourceInternalResistanceModuleTest {
    private static final double TOLERANCE = 1.0e-12;

    @Test
    void goldenSourceLoadCaseSatisfiesKirchhoffAndPowerBalance() {
        BoundPhysicsModule module = new PhysicsModuleRegistry(java.util.List.of(new SourceInternalResistanceModule()))
                .bind(SourceInternalResistanceModule.NUMERICAL_SOLVER_ID,
                        SourceInternalResistanceModule.REFERENCE_SOLVER_ID,
                        quantities(12, 1, 5));

        SolverOutput output = module.solve(new SimulationClock(4, 0.25));
        assertEquals(java.util.List.of(0.0), output.time());
        assertEquals(2.0, only(output, "current"), TOLERANCE);
        assertEquals(10.0, only(output, "terminalVoltage"), TOLERANCE);
        assertEquals(20.0, only(output, "loadPower"), TOLERANCE);
        assertEquals(4.0, only(output, "internalPowerLoss"), TOLERANCE);
        assertEquals(5.0 / 6.0, only(output, "efficiency"), TOLERANCE);

        assertEquals(12.0, 10.0 + 2.0, TOLERANCE);
        assertEquals(12.0 * 2.0, 20.0 + 4.0, TOLERANCE);
        assertEquals(20.0 / (12.0 * 2.0), 5.0 / 6.0, TOLERANCE);

        var reference = module.reference(0.75).values();
        assertEquals(2.0, reference.get("current"), TOLERANCE);
        assertEquals(10.0, reference.get("terminalVoltage"), TOLERANCE);
        assertEquals(20.0, reference.get("loadPower"), TOLERANCE);
        assertEquals(4.0, reference.get("internalPowerLoss"), TOLERANCE);
        assertEquals(5.0 / 6.0, reference.get("efficiency"), TOLERANCE);
        assertEquals(12.0 * reference.get("current"),
                reference.get("loadPower") + reference.get("internalPowerLoss"), TOLERANCE);
    }

    @Test
    void bindingRejectsNonPositiveSourceOrResistanceAndInvalidCheckpoint() {
        SourceInternalResistanceModule source = new SourceInternalResistanceModule();
        assertThrows(IllegalArgumentException.class, () -> source.bind(quantities(0, 1, 5)));
        assertThrows(IllegalArgumentException.class, () -> source.bind(quantities(12, 0, 5)));
        assertThrows(IllegalArgumentException.class, () -> source.bind(quantities(12, 1, 0)));

        BoundPhysicsModule bound = new PhysicsModuleRegistry(java.util.List.of(source))
                .bindModule(SourceInternalResistanceModule.MODULE_ID, quantities(12, 1, 5));
        assertThrows(IllegalArgumentException.class, () -> bound.reference(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> bound.reference(-0.1));
    }

    @Test
    void solverRejectsDerivedPowerOutsideFiniteNumericDomain() {
        SourceInternalResistanceModule source = new SourceInternalResistanceModule();
        var parameters = source.bind(quantities(1.0e308, 1.0e-308, 1.0e-308));
        assertThrows(IllegalArgumentException.class,
                () -> source.solve(parameters, new SimulationClock(1, 1)));
    }

    private static double only(SolverOutput output, String key) {
        assertEquals(1, output.values().get(key).size());
        return output.values().get(key).get(0);
    }

    private static CanonicalQuantityBag quantities(double emf, double internalResistance, double loadResistance) {
        return new CanonicalQuantityBag(
                Map.of(
                        "emf", BigDecimal.valueOf(emf),
                        "internal_resistance", BigDecimal.valueOf(internalResistance),
                        "load_resistance", BigDecimal.valueOf(loadResistance)),
                Map.of("emf", "V", "internal_resistance", "ohm", "load_resistance", "ohm"));
    }
}
