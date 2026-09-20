package com.example.backend.physics.module.kinematics;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KinematicsGraphModulesTest {
    private static final double TOLERANCE = 1.0e-10;

    @Test
    void positionTimeGraphMatchesHandCalculatedConstantAccelerationDisplacement() {
        var module = new PositionTimeGraphModule();
        var bound = new PhysicsModuleRegistry(java.util.List.of(module)).bind(
                PositionTimeGraphModule.NUMERICAL_SOLVER_ID, PositionTimeGraphModule.REFERENCE_SOLVER_ID,
                quantities(2.0, 3.0, 2.0));
        var output = bound.solve(new SimulationClock(2.0, 0.5));
        assertEquals(12.0, output.values().get("x").getLast(), TOLERANCE);
        assertEquals(12.0, bound.reference(2.0).values().get("x"), TOLERANCE);
        assertTrue(output.positions().containsKey("position"));
    }

    @Test
    void positionGraphAllowsNegativeAccelerationAndRejectsWrongCanonicalUnit() {
        var module = new PositionTimeGraphModule();
        var p = module.bind(quantities(4.0, 2.0, -2.0));
        assertEquals(4.0, module.solve(p, new SimulationClock(2.0, 1.0)).values().get("x").getLast(), TOLERANCE);
        assertThrows(IllegalArgumentException.class, () -> module.bind(new CanonicalQuantityBag(
                Map.of("initial_position", BigDecimal.ONE, "initial_velocity", BigDecimal.ONE, "acceleration", BigDecimal.ONE),
                Map.of("initial_position", "cm", "initial_velocity", "m/s", "acceleration", "m/s2"))));
    }

    @Test
    void velocityTimeGraphMatchesHandCalculatedSignedVelocity() {
        var module = new VelocityTimeGraphModule();
        var bound = new PhysicsModuleRegistry(java.util.List.of(module)).bind(
                VelocityTimeGraphModule.NUMERICAL_SOLVER_ID, VelocityTimeGraphModule.REFERENCE_SOLVER_ID,
                quantities(5.0, -2.0, 1.5));
        var output = bound.solve(new SimulationClock(4.0, 1.0));
        assertEquals(4.0, output.values().get("vx").getLast(), TOLERANCE);
        assertEquals(4.0, bound.reference(4.0).values().get("vx"), TOLERANCE);
        assertTrue(output.velocities().containsKey("velocity"));
    }

    @Test
    void velocityGraphZeroAccelerationBoundaryPreservesInitialVelocity() {
        var module = new VelocityTimeGraphModule();
        var output = module.solve(module.bind(quantities(-3.0, -4.0, 0.0)), new SimulationClock(3.0, 1.0));
        assertEquals(java.util.List.of(-4.0, -4.0, -4.0, -4.0), output.values().get("vx"));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(module.bind(quantities(0.0, 1.0, 1.0)), Double.NaN));
    }

    @Test
    void accelerationTimeGraphReportsConstantSignedAccelerationAtEverySample() {
        var module = new AccelerationTimeGraphModule();
        var bound = new PhysicsModuleRegistry(java.util.List.of(module)).bind(
                AccelerationTimeGraphModule.NUMERICAL_SOLVER_ID, AccelerationTimeGraphModule.REFERENCE_SOLVER_ID,
                quantities(1.0, 2.0, -3.0));
        var output = bound.solve(new SimulationClock(1.0, 0.25));
        assertEquals(java.util.List.of(-3.0, -3.0, -3.0, -3.0, -3.0), output.values().get("ax"));
        assertEquals(-3.0, bound.reference(0.75).values().get("ax"), TOLERANCE);
        assertTrue(output.accelerations().containsKey("acceleration"));
    }

    @Test
    void accelerationGraphRejectsWrongUnitAndAcceptsZeroAcceleration() {
        var module = new AccelerationTimeGraphModule();
        assertEquals(0.0, module.solve(module.bind(quantities(0.0, 0.0, 0.0)),
                new SimulationClock(0.5, 0.5)).values().get("ax").getFirst(), 0.0);
        assertThrows(IllegalArgumentException.class, () -> module.bind(new CanonicalQuantityBag(
                Map.of("initial_position", BigDecimal.ZERO, "initial_velocity", BigDecimal.ZERO, "acceleration", BigDecimal.ONE),
                Map.of("initial_position", "m", "initial_velocity", "m/s", "acceleration", "m/s"))));
    }

    private static CanonicalQuantityBag quantities(double position, double velocity, double acceleration) {
        return new CanonicalQuantityBag(Map.of("initial_position", BigDecimal.valueOf(position),
                        "initial_velocity", BigDecimal.valueOf(velocity), "acceleration", BigDecimal.valueOf(acceleration)),
                Map.of("initial_position", "m", "initial_velocity", "m/s", "acceleration", "m/s2"));
    }
}
