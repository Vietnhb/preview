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

class GravityOrbitModuleTest {
    private static final double RELATIVE_TOLERANCE = 1.0e-9;
    private final GravityOrbitModule module = new GravityOrbitModule();

    @Test
    void registryBindingMatchesEarthOrbitHandCalculationAndIndependentReference() {
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                GravityOrbitModule.NUMERICAL_SOLVER_ID,
                GravityOrbitModule.REFERENCE_SOLVER_ID,
                quantities(5.972e24, 1_000.0, 7.0e6));
        SolverOutput output = bound.solve(new SimulationClock(1.0, 0.5));

        assertEquals(List.of(0.0, 0.5, 1.0), output.time());
        assertSeries(output.values().get("gravitationalForce"), 8_134.4733877551025);
        assertSeries(output.values().get("gravitationalField"), 8.134473387755103);
        assertSeries(output.values().get("orbitalSpeed"), 7_545.946840144431);
        assertSeries(output.values().get("orbitalPeriod"), 5_828.598860022617);
        for (int index = 0; index < output.time().size(); index++) {
            var reference = bound.reference(output.time().get(index)).values();
            assertEquals(output.values().get("gravitationalForce").get(index),
                    reference.get("gravitationalForce"), 1.0e-8);
            assertEquals(output.values().get("gravitationalField").get(index),
                    reference.get("gravitationalField"), 1.0e-12);
            assertEquals(output.values().get("orbitalSpeed").get(index),
                    reference.get("orbitalSpeed"), 1.0e-9);
            assertEquals(output.values().get("orbitalPeriod").get(index),
                    reference.get("orbitalPeriod"), 1.0e-8);
        }
    }

    @Test
    void acceptsPositiveBoundaryInputsAndRejectsNonPositiveMassesOrRadius() {
        var boundary = module.bind(quantities(Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE));
        assertEquals(Double.MIN_VALUE, boundary.centralMass());

        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.0, 1.0, 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, -1.0, 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, 1.0, 0.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new GravityOrbitModule.Parameters(Double.POSITIVE_INFINITY, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(boundary, -0.01));
    }

    @Test
    void rejectsInputsWhoseDerivedOutputsOverflowTheNumericDomain() {
        var extreme = module.bind(quantities(1.0e308, 1.0e308, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(extreme, new SimulationClock(0.0, 0.1)));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(extreme, 0.0));
    }

    private static void assertSeries(List<Double> actual, double expected) {
        assertEquals(3, actual.size());
        for (double value : actual) {
            assertEquals(expected, value, Math.max(1.0e-12, Math.abs(expected) * RELATIVE_TOLERANCE));
        }
    }

    private static CanonicalQuantityBag quantities(double centralMass, double satelliteMass, double radius) {
        return new CanonicalQuantityBag(
                Map.of("central_mass", decimal(centralMass),
                        "satellite_mass", decimal(satelliteMass),
                        "orbit_radius", decimal(radius)),
                Map.of("central_mass", "kg", "satellite_mass", "kg", "orbit_radius", "m"));
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }
}
