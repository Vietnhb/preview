package com.example.backend.physics.module.dynamics;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicsForcesCollisionSpringModuleTest {
    private static final double TOLERANCE = 1.0e-9;

    @Test
    void forceModuleMatchesHandCalculatedFrictionlessMotionAndIndependentAverageVelocityOracle() {
        var module = new DynamicsForcesModule();
        var bound = new PhysicsModuleRegistry(java.util.List.of(module)).bind(
                DynamicsForcesModule.NUMERICAL_SOLVER_ID, DynamicsForcesModule.REFERENCE_SOLVER_ID,
                quantities(Map.of("mass", 2.0, "net_force", 4.0, "friction_coefficient", 0.0,
                        "initial_position", 1.0, "initial_velocity", 3.0, "gravitational_acceleration", 9.81),
                        Map.of("mass", "kg", "net_force", "N", "friction_coefficient", "1",
                                "initial_position", "m", "initial_velocity", "m/s", "gravitational_acceleration", "m/s2")));
        var output = bound.solve(new SimulationClock(2.0, 1.0));
        assertEquals(11.0, output.values().get("x").getLast(), TOLERANCE);
        assertEquals(7.0, output.values().get("vx").getLast(), TOLERANCE);
        assertEquals(2.0, output.values().get("ax").getLast(), TOLERANCE);
        assertEquals(4.0, output.values().get("force").getLast(), TOLERANCE);
        assertEquals(11.0, bound.reference(2.0).values().get("x"), TOLERANCE);
        assertEquals(7.0, bound.reference(2.0).values().get("vx"), TOLERANCE);
        assertTrue(output.positions().containsKey("position"));
        assertTrue(output.velocities().containsKey("velocity"));
        assertTrue(output.accelerations().containsKey("acceleration"));
    }

    @Test
    void staticFrictionHoldsAtRestAndForceInputsRejectInvalidDomain() {
        var module = new DynamicsForcesModule();
        var held = module.bind(quantities(Map.of("mass", 2.0, "net_force", 2.0, "friction_coefficient", 0.5,
                "initial_position", -3.0, "initial_velocity", 0.0, "gravitational_acceleration", 9.81),
                Map.of("mass", "kg", "net_force", "N", "friction_coefficient", "1", "initial_position", "m",
                        "initial_velocity", "m/s", "gravitational_acceleration", "m/s2")));
        var output = module.solve(held, new SimulationClock(1.0, 0.5));
        assertEquals(java.util.List.of(-3.0, -3.0, -3.0), output.values().get("x"));
        assertEquals(java.util.List.of(0.0, 0.0, 0.0), output.values().get("vx"));
        assertEquals(0.0, output.values().get("ax").getLast(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(Map.of(
                "mass", 0.0, "net_force", 2.0, "friction_coefficient", 0.2, "initial_position", 0.0,
                "initial_velocity", 0.0, "gravitational_acceleration", 9.81), Map.of("mass", "kg", "net_force", "N",
                "friction_coefficient", "1", "initial_position", "m", "initial_velocity", "m/s",
                "gravitational_acceleration", "m/s2"))));
    }

    @Test
    void collisionExchangesVelocitiesForEqualMassesAndConservesMomentumAndEnergy() {
        var module = new DynamicsCollisionModule();
        var bound = new PhysicsModuleRegistry(java.util.List.of(module)).bind(
                DynamicsCollisionModule.NUMERICAL_SOLVER_ID, DynamicsCollisionModule.REFERENCE_SOLVER_ID,
                collisionQuantities(1.0, 1.0, 0.0, 4.0, 4.0, 0.0));
        var output = bound.solve(new SimulationClock(1.5, 0.5));
        assertEquals(1.0, output.time().get(2), TOLERANCE);
        assertEquals(4.0, output.values().get("position1").get(2), TOLERANCE);
        assertEquals(4.0, output.values().get("position2").get(2), TOLERANCE);
        assertEquals(0.0, output.values().get("velocity1").get(2), TOLERANCE);
        assertEquals(4.0, output.values().get("velocity2").get(2), TOLERANCE);
        assertEquals(4.0, output.values().get("position1").getLast(), TOLERANCE);
        assertEquals(6.0, output.values().get("position2").getLast(), TOLERANCE);
        double initialMomentum = 1.0 * 4.0 + 1.0 * 0.0;
        double finalMomentum = 1.0 * output.values().get("velocity1").getLast()
                + 1.0 * output.values().get("velocity2").getLast();
        double initialEnergy = 0.5 * 4.0 * 4.0;
        double finalEnergy = 0.5 * output.values().get("velocity1").getLast() * output.values().get("velocity1").getLast()
                + 0.5 * output.values().get("velocity2").getLast() * output.values().get("velocity2").getLast();
        assertEquals(initialMomentum, finalMomentum, TOLERANCE);
        assertEquals(initialEnergy, finalEnergy, TOLERANCE);
        assertEquals(output.values().get("velocity1").getLast(), bound.reference(1.5).values().get("velocity1"), TOLERANCE);
    }

    @Test
    void collisionMovingApartDoesNotTriggerAndInvalidMassesAreRejected() {
        var module = new DynamicsCollisionModule();
        var p = module.bind(collisionQuantities(1.0, 2.0, 0.0, 3.0, -1.0, 1.0));
        var output = module.solve(p, new SimulationClock(2.0, 1.0));
        assertEquals(-2.0, output.values().get("position1").getLast(), TOLERANCE);
        assertEquals(5.0, output.values().get("position2").getLast(), TOLERANCE);
        assertEquals(-1.0, output.values().get("velocity1").getLast(), TOLERANCE);
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(collisionQuantities(0.0, 2.0, 0.0, 3.0, 1.0, 0.0)));
    }

    @Test
    void springGoldenQuarterPeriodMatchesAnalyticalHarmonicOracle() {
        var module = new SpringOscillationModule();
        var bound = new PhysicsModuleRegistry(java.util.List.of(module)).bind(
                SpringOscillationModule.NUMERICAL_SOLVER_ID, SpringOscillationModule.REFERENCE_SOLVER_ID,
                quantities(Map.of("amplitude", 0.2, "mass", 2.0, "spring_constant", 8.0, "phase", 0.0),
                        Map.of("amplitude", "m", "mass", "kg", "spring_constant", "N/m", "phase", "rad")));
        var initial = bound.solve(new SimulationClock(0.1, 0.05));
        assertEquals(0.2, initial.values().get("x").getFirst(), TOLERANCE);
        assertEquals(0.0, initial.values().get("vx").getFirst(), TOLERANCE);
        assertEquals(-0.8, initial.values().get("ax").getFirst(), TOLERANCE);
        var quarter = bound.reference(Math.PI / 4.0).values();
        assertEquals(0.0, quarter.get("x"), 1.0e-15);
        assertEquals(-0.4, quarter.get("vx"), TOLERANCE);
        assertEquals(0.0, quarter.get("ax"), 1.0e-14);
        assertTrue(Math.abs(initial.values().get("x").getLast()
                - bound.reference(0.1).values().get("x")) < 1.0e-4);
    }

    @Test
    void springRejectsZeroAmplitudeAndKeepsFinitePhaseBoundary() {
        var module = new SpringOscillationModule();
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(Map.of("amplitude", 0.0,
                "mass", 2.0, "spring_constant", 8.0, "phase", Math.PI / 2.0),
                Map.of("amplitude", "m", "mass", "kg", "spring_constant", "N/m", "phase", "rad"))));
        var p = module.bind(quantities(Map.of("amplitude", 0.2, "mass", 2.0, "spring_constant", 8.0,
                "phase", Math.PI / 2.0), Map.of("amplitude", "m", "mass", "kg", "spring_constant", "N/m", "phase", "rad")));
        assertEquals(0.0, module.solve(p, new SimulationClock(0.1, 0.1)).values().get("x").getFirst(), 1.0e-15);
        assertEquals(-0.4, module.referenceAt(p, 0.0).values().get("vx"), TOLERANCE);
    }

    private static CanonicalQuantityBag collisionQuantities(double m1, double m2, double x1, double x2,
                                                             double v1, double v2) {
        return quantities(Map.of("mass_1", m1, "mass_2", m2, "initial_position_1", x1,
                "initial_position_2", x2, "velocity_1", v1, "velocity_2", v2),
                Map.of("mass_1", "kg", "mass_2", "kg", "initial_position_1", "m", "initial_position_2", "m",
                        "velocity_1", "m/s", "velocity_2", "m/s"));
    }

    private static CanonicalQuantityBag quantities(Map<String, Double> values, Map<String, String> units) {
        var decimals = new java.util.LinkedHashMap<String, BigDecimal>();
        values.forEach((key, value) -> decimals.put(key, BigDecimal.valueOf(value)));
        return new CanonicalQuantityBag(decimals, units);
    }
}
