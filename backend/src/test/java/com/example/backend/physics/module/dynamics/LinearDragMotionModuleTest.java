package com.example.backend.physics.module.dynamics;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinearDragMotionModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private static final Set<String> OUTPUT_KEYS = Set.of("position", "velocity", "acceleration", "dragForce");

    @Test
    void handComputedGoldenMatchesTheIndependentReferenceAndForceBalance() {
        LinearDragMotionModule module = new LinearDragMotionModule();
        var parameters = module.bind(quantities(2, 1, 3, 4, 2));
        SolverOutput output = module.solve(parameters, new SimulationClock(0.5, 0.5));

        assertEquals("linear_drag_motion", module.moduleId());
        assertEquals("linear_drag_solver_v2", module.numericalSolverId());
        assertEquals("linear_drag_reference_v2", module.referenceSolverId());
        assertEquals(java.util.List.of(0.0, 0.5), output.time());
        assertEquals(OUTPUT_KEYS, output.values().keySet());
        assertSeries(output.values().get("position"), 1.0, 2.3934693402873666);
        assertSeries(output.values().get("velocity"), 3.0, 2.6065306597126334);
        assertSeries(output.values().get("acceleration"), -1.0, -0.6065306597126334);
        assertSeries(output.values().get("dragForce"), -6.0, -5.213061319425267);
        assertEquals(output.values().get("position"), output.positions().get("x"));
        assertEquals(output.values().get("velocity"), output.velocities().get("x"));
        assertEquals(output.values().get("acceleration"), output.accelerations().get("x"));

        for (int index = 0; index < output.time().size(); index++) {
            double time = output.time().get(index);
            var point = module.referenceAt(parameters, time).values();
            for (String key : OUTPUT_KEYS) {
                assertEquals(output.values().get(key).get(index), point.get(key), TOLERANCE, key);
            }
            double acceleration = output.values().get("acceleration").get(index);
            double velocity = output.values().get("velocity").get(index);
            double drag = output.values().get("dragForce").get(index);
            assertEquals(parameters.constantForce() + drag, parameters.mass() * acceleration, TOLERANCE);
            assertEquals(-parameters.dragCoefficient() * velocity, drag, TOLERANCE);
        }
    }

    @Test
    void referenceUsesStableSmallTimeFactorsAndAcceptsSignedForceAndVelocity() {
        LinearDragMotionModule module = new LinearDragMotionModule();
        var parameters = module.bind(quantities(2, -4, -3, -2, 2));
        double time = 1.0e-8;
        var point = module.referenceAt(parameters, time).values();

        assertEquals(-4.0, point.get("position") - (-3.0 * time), 1.0e-15);
        assertEquals(-3.0 + 2.0 * time, point.get("velocity"), 1.0e-15);
        assertEquals(2.0, point.get("acceleration"), 1.0e-7);
        assertEquals(6.0, point.get("dragForce"), 1.0e-7);

        SolverOutput output = module.solve(parameters, new SimulationClock(0.1, 0.1));
        assertTrue(output.values().get("velocity").stream().allMatch(Double::isFinite));
    }

    @Test
    void rejectsWrongUnitsInvalidDomainsAndNonFiniteReferenceTimes() {
        LinearDragMotionModule module = new LinearDragMotionModule();
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(2, 1, 3, 4, 2, "drag_coefficient", "N")));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0, 1, 3, 4, 2)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(2, 1, 3, 4, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> new LinearDragMotionModule.Parameters(1, Double.NaN, 0, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new LinearDragMotionModule.Parameters(1, 0, 0, 0, Double.POSITIVE_INFINITY));
        var valid = module.bind(quantities(2, 1, 3, 4, 2));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, -1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, Double.NaN));
    }

    @Test
    void rejectsNumericallyUnrepresentableRatesAndOutputs() {
        LinearDragMotionModule module = new LinearDragMotionModule();
        var unrepresentableRate = module.bind(quantities(Double.MIN_VALUE, 0, 0, 0, 1));
        assertThrows(ArithmeticException.class,
                () -> module.solve(unrepresentableRate, new SimulationClock(1, 1)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(unrepresentableRate, 0));

        var overflowedTerminalSpeed = module.bind(quantities(1, 0, 0, Double.MAX_VALUE, Double.MIN_NORMAL));
        assertThrows(ArithmeticException.class,
                () -> module.solve(overflowedTerminalSpeed, new SimulationClock(0.1, 0.1)));
    }

    private static CanonicalQuantityBag quantities(double mass, double position, double velocity,
                                                   double force, double dragCoefficient) {
        return quantities(mass, position, velocity, force, dragCoefficient, null, null);
    }

    private static CanonicalQuantityBag quantities(double mass, double position, double velocity,
                                                   double force, double dragCoefficient,
                                                   String overrideKey, String overrideUnit) {
        Map<String, BigDecimal> values = Map.of(
                "mass", BigDecimal.valueOf(mass),
                "initial_position", BigDecimal.valueOf(position),
                "initial_velocity", BigDecimal.valueOf(velocity),
                "constant_force", BigDecimal.valueOf(force),
                "drag_coefficient", BigDecimal.valueOf(dragCoefficient));
        Map<String, String> units = new java.util.LinkedHashMap<>(Map.of(
                "mass", "kg",
                "initial_position", "m",
                "initial_velocity", "m/s",
                "constant_force", "N",
                "drag_coefficient", "kg/s"));
        if (overrideKey != null) units.put(overrideKey, overrideUnit);
        return new CanonicalQuantityBag(values, units);
    }

    private static void assertSeries(java.util.List<Double> actual, double first, double second) {
        assertEquals(2, actual.size());
        assertEquals(first, actual.get(0), TOLERANCE);
        assertEquals(second, actual.get(1), TOLERANCE);
    }
}
