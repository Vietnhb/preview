package com.example.backend.physics.module.dynamics;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniformAccelerationModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private static final Set<String> OUTPUT_KEYS = Set.of(
            "x", "displacement", "y", "vx", "vy", "ax", "ay");

    private final UniformAccelerationModule module = new UniformAccelerationModule();

    @Test
    void handComputedGoldenMatchesIndependentReferenceAndKeepsLegacyOutputShape() {
        var parameters = module.bind(quantities(5.0, 2.0, 3.0));
        SolverOutput output = module.solve(parameters, new SimulationClock(1.0, 0.5));

        assertEquals("uniform_acceleration", module.moduleId());
        assertEquals("uniform_acceleration_solver_v2", module.numericalSolverId());
        assertEquals("uniform_acceleration_reference_v2", module.referenceSolverId());
        assertEquals(List.of(0.0, 0.5, 1.0), output.time());
        assertEquals(OUTPUT_KEYS, output.values().keySet());
        assertSeries(output, "x", 5.0, 6.375, 8.5);
        assertSeries(output, "displacement", 0.0, 1.375, 3.5);
        assertSeries(output, "y", 0.0, 0.0, 0.0);
        assertSeries(output, "vx", 2.0, 3.5, 5.0);
        assertSeries(output, "vy", 0.0, 0.0, 0.0);
        assertSeries(output, "ax", 3.0, 3.0, 3.0);
        assertSeries(output, "ay", 0.0, 0.0, 0.0);
        assertEquals(output.values().get("x"), output.positions().get("x"));
        assertEquals(output.values().get("vx"), output.velocities().get("x"));
        assertEquals(output.values().get("ax"), output.accelerations().get("x"));

        for (int index = 0; index < output.time().size(); index++) {
            double time = output.time().get(index);
            Map<String, Double> reference = module.referenceAt(parameters, time).values();
            for (String key : OUTPUT_KEYS) {
                assertEquals(output.values().get(key).get(index), reference.get(key), TOLERANCE, key);
            }
        }
    }

    @Test
    void signedMotionAndFinalPartialStepPreserveKinematicInvariants() {
        var parameters = module.bind(quantities(-2.0, 4.0, -2.0));
        SolverOutput output = module.solve(parameters, new SimulationClock(0.8, 0.3));

        assertEquals(List.of(0.0, 0.3, 0.6, 0.8), output.time());
        assertEquals(0.56, output.values().get("x").getLast(), TOLERANCE);
        assertEquals(2.56, output.values().get("displacement").getLast(), TOLERANCE);
        assertEquals(2.4, output.values().get("vx").getLast(), TOLERANCE);
        for (int index = 0; index < output.time().size(); index++) {
            double time = output.time().get(index);
            double velocity = output.values().get("vx").get(index);
            double position = output.values().get("x").get(index);
            assertEquals(parameters.initialVelocity() + parameters.acceleration() * time,
                    velocity, TOLERANCE);
            assertEquals(parameters.initialPosition() + (parameters.initialVelocity() + velocity) * time / 2.0,
                    position, TOLERANCE);
        }
    }

    @Test
    void rejectsMissingInputsNonCanonicalUnitsAndInvalidReferenceTimes() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.0, 1.0, 1.0, "acceleration", "m/s2.0")));
        assertThrows(IllegalArgumentException.class,
                () -> new UniformAccelerationModule.Parameters(Double.NaN, 0.0, 0.0));

        var valid = module.bind(quantities(0.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, -1.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, Double.POSITIVE_INFINITY));
    }

    @Test
    void rejectsNonFiniteIntegratedAndReferenceResults() {
        var extreme = module.bind(quantities(Double.MAX_VALUE, Double.MAX_VALUE, 0.0));
        assertThrows(ArithmeticException.class,
                () -> module.solve(extreme, new SimulationClock(2.0, 2.0)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(extreme, 2.0));

        SolverOutput finite = module.solve(module.bind(quantities(1.0, -3.0, 0.0)),
                new SimulationClock(0.4, 0.2));
        assertTrue(finite.values().values().stream().flatMap(List::stream).allMatch(Double::isFinite));
    }

    private static void assertSeries(SolverOutput output, String key, double... expected) {
        List<Double> actual = output.values().get(key);
        assertEquals(expected.length, actual.size(), key);
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], actual.get(index), TOLERANCE, key);
        }
    }

    private static CanonicalQuantityBag quantities(double position, double velocity, double acceleration) {
        return quantities(position, velocity, acceleration, null, null);
    }

    private static CanonicalQuantityBag quantities(double position, double velocity, double acceleration,
                                                   String overrideKey, String overrideUnit) {
        Map<String, BigDecimal> values = Map.of(
                "initial_position", BigDecimal.valueOf(position),
                "initial_velocity", BigDecimal.valueOf(velocity),
                "acceleration", BigDecimal.valueOf(acceleration));
        Map<String, String> units = new java.util.LinkedHashMap<>(Map.of(
                "initial_position", "m",
                "initial_velocity", "m/s",
                "acceleration", "m/s2"));
        if (overrideKey != null) units.put(overrideKey, overrideUnit);
        return new CanonicalQuantityBag(values, units);
    }
}
