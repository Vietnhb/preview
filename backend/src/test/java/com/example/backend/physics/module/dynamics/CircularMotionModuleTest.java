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
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircularMotionModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private final CircularMotionModule module = new CircularMotionModule();

    @Test
    void typedRegistryProducesHandCalculatedGoldenCaseAndIndependentReference() {
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                CircularMotionModule.NUMERICAL_SOLVER_ID,
                CircularMotionModule.REFERENCE_SOLVER_ID,
                quantities(2.0, 4.0, 6.0));
        SolverOutput output = bound.solve(new SimulationClock(1.0, 0.5));

        assertEquals(List.of(0.0, 0.5, 1.0), output.time());
        assertSeries(output, "angularSpeed", 1.5);
        assertSeries(output, "period", 4.1887902047863905);
        assertSeries(output, "frequency", 0.238732414637843);
        assertSeries(output, "centripetalAcceleration", 9.0);
        assertSeries(output, "centripetalForce", 18.0);

        for (double time : output.time()) {
            Map<String, Double> oracle = bound.reference(time).values();
            output.values().forEach((key, series) ->
                    assertEquals(series.get(output.time().indexOf(time)), oracle.get(key), TOLERANCE, key));
        }
    }

    @Test
    void outputsSatisfyCircularMotionInvariants() {
        var parameters = module.bind(quantities(3.0, 2.5, 7.0));
        SolverOutput output = module.solve(parameters, new SimulationClock(0.2, 0.1));
        for (int index = 0; index < output.time().size(); index++) {
            double angularSpeed = output.values().get("angularSpeed").get(index);
            double period = output.values().get("period").get(index);
            double frequency = output.values().get("frequency").get(index);
            double acceleration = output.values().get("centripetalAcceleration").get(index);
            double force = output.values().get("centripetalForce").get(index);

            assertEquals(2.0 * Math.PI, angularSpeed * period, TOLERANCE);
            assertEquals(1.0, period * frequency, TOLERANCE);
            assertEquals(parameters.speed() * parameters.speed(), acceleration * parameters.radius(), TOLERANCE);
            assertEquals(parameters.mass() * acceleration, force, TOLERANCE);
        }
    }

    @Test
    void rejectsMissingOrInvalidPhysicalInputsAndInvalidCheckpointTime() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.0, 1.0, 1.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, -1.0, 1.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, 1.0, 0.0)));
        CanonicalQuantityBag wrongUnit = new CanonicalQuantityBag(
                Map.of("mass", BigDecimal.ONE, "radius", BigDecimal.ONE, "speed", BigDecimal.ONE),
                Map.of("mass", "g", "radius", "m", "speed", "m/s"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));
        assertThrows(IllegalArgumentException.class,
                () -> new CircularMotionModule.Parameters(Double.NaN, 1.0, 1.0));

        var valid = module.bind(quantities(1.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, Double.NaN));
    }

    @Test
    void rejectsInputsWhoseDerivedNumericalOrOracleOutputsAreNotFinite() {
        var extreme = module.bind(quantities(1.0e308, 1.0e-308, 1.0e308));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(extreme, new SimulationClock(0.1, 0.1)));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(extreme, 0.0));

        var normal = module.solve(module.bind(quantities(2.0, 4.0, 6.0)), new SimulationClock(0.1, 0.1));
        assertTrue(normal.values().values().stream().flatMap(List::stream).allMatch(Double::isFinite));
    }

    private static void assertSeries(SolverOutput output, String key, double expected) {
        List<Double> actual = output.values().get(key);
        assertEquals(output.time().size(), actual.size(), key);
        for (double value : actual) assertEquals(expected, value, TOLERANCE, key);
    }

    private static CanonicalQuantityBag quantities(double mass, double radius, double speed) {
        return new CanonicalQuantityBag(
                Map.of("mass", BigDecimal.valueOf(mass),
                        "radius", BigDecimal.valueOf(radius),
                        "speed", BigDecimal.valueOf(speed)),
                Map.of("mass", "kg", "radius", "m", "speed", "m/s"));
    }
}
