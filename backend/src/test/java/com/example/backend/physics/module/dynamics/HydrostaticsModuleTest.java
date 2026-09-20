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

class HydrostaticsModuleTest {
    private static final double TOLERANCE = 1.0e-10;
    private final HydrostaticsModule module = new HydrostaticsModule();

    @Test
    void registryBindingMatchesHandCalculatedGoldenCaseAndFluidInvariants() {
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                HydrostaticsModule.NUMERICAL_SOLVER_ID, HydrostaticsModule.REFERENCE_SOLVER_ID,
                quantities(1000.0, 3.0, 0.02, 9.81, 101325.0));
        SolverOutput output = bound.solve(new SimulationClock(0.2, 0.1));

        assertEquals(List.of(0.0, 0.1, 0.2), output.time());
        for (int index = 0; index < output.time().size(); index++) {
            double gauge = output.values().get("gaugePressure").get(index);
            double absolute = output.values().get("absolutePressure").get(index);
            double buoyancy = output.values().get("buoyantForce").get(index);
            assertEquals(29_430.0, gauge, TOLERANCE);
            assertEquals(130_755.0, absolute, TOLERANCE);
            assertEquals(196.2, buoyancy, TOLERANCE);
            var oracle = bound.reference(output.time().get(index));
            assertEquals(gauge, oracle.values().get("gaugePressure"), TOLERANCE);
            assertEquals(absolute, oracle.values().get("absolutePressure"), TOLERANCE);
            assertEquals(buoyancy, oracle.values().get("buoyantForce"), TOLERANCE);
            assertEquals(29_430.0, absolute - 101_325.0, TOLERANCE);
            assertEquals(9_810.0, buoyancy / 0.02, TOLERANCE);
        }
    }

    @Test
    void surfaceBoundaryPreservesAtmosphericPressureAndRejectsInvalidDomain() {
        SolverOutput output = module.solve(module.bind(quantities(1000.0, 0.0, 0.02, 9.81, 101325.0)),
                new SimulationClock(0.1, 0.1));
        assertEquals(0.0, output.values().get("gaugePressure").get(0), TOLERANCE);
        assertEquals(101325.0, output.values().get("absolutePressure").get(0), TOLERANCE);
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.0, 1.0, 0.02, 9.81, 101325.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1000.0, -1.0, 0.02, 9.81, 101325.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1000.0, 1.0, -0.02, 9.81, 101325.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1000.0, 1.0, 0.02, 0.0, 101325.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1000.0, 1.0, 0.02, 9.81, -1.0)));
    }

    private static CanonicalQuantityBag quantities(double density, double depth, double volume,
                                                    double gravity, double atmosphericPressure) {
        return new CanonicalQuantityBag(
                Map.of("fluid_density", BigDecimal.valueOf(density),
                        "depth", BigDecimal.valueOf(depth),
                        "displaced_volume", BigDecimal.valueOf(volume),
                        "gravitational_acceleration", BigDecimal.valueOf(gravity),
                        "atmospheric_pressure", BigDecimal.valueOf(atmosphericPressure)),
                Map.of("fluid_density", "kg/m3", "depth", "m", "displaced_volume", "m3",
                        "gravitational_acceleration", "m/s2", "atmospheric_pressure", "Pa"));
    }
}
