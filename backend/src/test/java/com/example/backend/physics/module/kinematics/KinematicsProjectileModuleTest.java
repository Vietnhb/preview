package com.example.backend.physics.module.kinematics;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KinematicsProjectileModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private static final Set<String> OUTPUT_KEYS = Set.of(
            "x", "displacement", "y", "vx", "vy", "ax", "ay");

    private final KinematicsProjectileModule module = new KinematicsProjectileModule();

    @Test
    void handComputedGoldenCaseMatchesAverageVelocityReferenceAtEverySample() {
        var parameters = module.bind(quantities(3.0, 5.0, Math.sqrt(2.0), Math.PI / 4.0, 2.0));
        SolverOutput output = module.solve(parameters, new SimulationClock(1.1, 0.4));

        assertEquals("kinematics_projectile", module.moduleId());
        assertEquals("kinematics_projectile_solver_v2", module.numericalSolverId());
        assertEquals("kinematics_projectile_reference_v2", module.referenceSolverId());
        assertEquals(List.of(0.0, 0.4, 0.8, 1.1), output.time());
        assertEquals(OUTPUT_KEYS, output.values().keySet());
        assertSeries(output, "x", 3.0, 3.4, 3.8, 4.1);
        assertSeries(output, "displacement", 0.0, 0.4, 0.8, 1.1);
        assertSeries(output, "y", 5.0, 5.24, 5.16, 4.89);
        assertSeries(output, "vx", 1.0, 1.0, 1.0, 1.0);
        assertSeries(output, "vy", 1.0, 0.2, -0.6, -1.2);
        assertSeries(output, "ax", 0.0, 0.0, 0.0, 0.0);
        assertSeries(output, "ay", -2.0, -2.0, -2.0, -2.0);
        assertEquals(output.values().get("x"), output.positions().get("x"));
        assertEquals(output.values().get("y"), output.positions().get("y"));
        assertEquals(output.values().get("vx"), output.velocities().get("x"));
        assertEquals(output.values().get("vy"), output.velocities().get("y"));

        for (int index = 0; index < output.time().size(); index++) {
            Map<String, Double> reference = module.referenceAt(parameters, output.time().get(index)).values();
            for (String key : OUTPUT_KEYS) {
                assertEquals(output.values().get(key).get(index), reference.get(key), TOLERANCE, key);
            }
        }
    }

    @Test
    void acceptsInclusiveLaunchAngleAndGravityBoundaries() {
        var horizontal = module.bind(quantities(2.0, 7.0, 3.0, 0.0, 0.0));
        SolverOutput horizontalOutput = module.solve(horizontal, new SimulationClock(1.0, 1.0));
        assertEquals(5.0, horizontalOutput.values().get("x").getLast(), TOLERANCE);
        assertEquals(7.0, horizontalOutput.values().get("y").getLast(), TOLERANCE);
        assertEquals(3.0, horizontalOutput.values().get("vx").getLast(), TOLERANCE);
        assertEquals(0.0, horizontalOutput.values().get("vy").getLast(), TOLERANCE);

        var maximumBoundary = module.bind(quantities(2.0, 7.0, 3.0, Math.PI, 30.0));
        SolverOutput boundaryOutput = module.solve(maximumBoundary, new SimulationClock(1.0, 1.0));
        assertEquals(-1.0, boundaryOutput.values().get("x").getLast(), TOLERANCE);
        assertEquals(-8.0, boundaryOutput.values().get("y").getLast(), TOLERANCE);
        assertEquals(-30.0, boundaryOutput.values().get("ay").getLast(), TOLERANCE);
    }

    @Test
    void rejectsMissingCanonicalInputsAndNonCanonicalUnits() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.0, 0.0, 1.0, Math.PI / 4.0, 9.81,
                        "initial_height", "ft")));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.0, 0.0, 1.0, Math.PI / 4.0, 9.81,
                        "launch_angle", "deg")));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.0, 0.0, 1.0, Math.PI / 4.0, 9.81,
                        "gravitational_acceleration", "m/s²")));
    }

    @Test
    void rejectsPhysicalDomainViolationsAndNonFiniteParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.0, 0.0, 0.0, Math.PI / 4.0, 9.81)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.0, 0.0, 1.0, -0.001, 9.81)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.0, 0.0, 1.0, Math.PI + 0.001, 9.81)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.0, 0.0, 1.0, Math.PI / 4.0, -0.001)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.0, 0.0, 1.0, Math.PI / 4.0, 30.001)));
        assertThrows(IllegalArgumentException.class,
                () -> new KinematicsProjectileModule.Parameters(Double.NaN, 0.0, 1.0, 0.0, 9.81));
    }

    @Test
    void boundsSimulationResourcesAndRejectsOverflowingTrajectories() {
        var parameters = module.bind(quantities(0.0, 0.0, 1.0, Math.PI / 4.0, 9.81));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(parameters, new SimulationClock(3_600.001, 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(parameters, new SimulationClock(20.0, 0.001)));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(parameters, new SimulationClock(1.0, 0.0000005)));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, 3_600.001));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, Double.NaN));

        var extreme = module.bind(quantities(Double.MAX_VALUE, 0.0, Double.MAX_VALUE, 0.0, 0.0));
        assertThrows(ArithmeticException.class,
                () -> module.solve(extreme, new SimulationClock(1.0, 1.0)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(extreme, 2.0));
    }

    private static void assertSeries(SolverOutput output, String key, double... expected) {
        List<Double> actual = output.values().get(key);
        assertEquals(expected.length, actual.size(), key);
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], actual.get(index), TOLERANCE, key);
        }
    }

    private static CanonicalQuantityBag quantities(double initialPosition, double initialHeight,
                                                   double initialVelocity, double launchAngle,
                                                   double gravity) {
        return quantities(initialPosition, initialHeight, initialVelocity, launchAngle, gravity, null, null);
    }

    private static CanonicalQuantityBag quantities(double initialPosition, double initialHeight,
                                                   double initialVelocity, double launchAngle,
                                                   double gravity, String unitOverrideKey,
                                                   String unitOverrideValue) {
        Map<String, BigDecimal> values = Map.of(
                "initial_position", BigDecimal.valueOf(initialPosition),
                "initial_height", BigDecimal.valueOf(initialHeight),
                "initial_velocity", BigDecimal.valueOf(initialVelocity),
                "launch_angle", BigDecimal.valueOf(launchAngle),
                "gravitational_acceleration", BigDecimal.valueOf(gravity));
        Map<String, String> units = new LinkedHashMap<>(Map.of(
                "initial_position", "m",
                "initial_height", "m",
                "initial_velocity", "m/s",
                "launch_angle", "rad",
                "gravitational_acceleration", "m/s2"));
        if (unitOverrideKey != null) units.put(unitOverrideKey, unitOverrideValue);
        return new CanonicalQuantityBag(values, units);
    }
}
