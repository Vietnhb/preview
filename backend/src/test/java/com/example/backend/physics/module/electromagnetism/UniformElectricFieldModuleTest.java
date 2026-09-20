package com.example.backend.physics.module.electromagnetism;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.BoundPhysicsModule;
import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UniformElectricFieldModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private final UniformElectricFieldModule module = new UniformElectricFieldModule();

    @Test
    void typedBindingProducesHandCalculatedOutputsAndIndependentReference() {
        assertEquals("uniform_electric_field_solver_v2", module.numericalSolverId());
        assertEquals("uniform_electric_field_reference_v2", module.referenceSolverId());
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                UniformElectricFieldModule.NUMERICAL_SOLVER_ID,
                UniformElectricFieldModule.REFERENCE_SOLVER_ID,
                quantities(2.0e-6, 1.0e-6, 100.0, 0.02, 3.0, 0.004));
        SolverOutput output = bound.solve(new SimulationClock(1.0, 0.5));

        assertEquals(List.of(0.0, 0.5, 1.0), output.time());
        assertEquals(Set.of("fieldStrength", "electricForce", "acceleration",
                "transverseDisplacement", "longitudinalDisplacement"), output.values().keySet());
        assertSeries(output, "fieldStrength", 5_000.0);
        assertSeries(output, "electricForce", 0.01);
        assertSeries(output, "acceleration", 10_000.0);
        assertSeries(output, "transverseDisplacement", 0.08);
        assertSeries(output, "longitudinalDisplacement", 0.012);

        Map<String, Double> reference = bound.reference(0.5).values();
        assertEquals(output.values().keySet(), reference.keySet());
        output.values().forEach((key, series) ->
                assertEquals(series.get(1), reference.get(key), TOLERANCE, key));
    }

    @Test
    void signedChargeReversesForceAccelerationAndTransverseMotion() {
        SolverOutput output = module.solve(module.bind(quantities(-2.0e-6, 1.0e-6,
                100.0, 0.02, 3.0, 0.004)), new SimulationClock(0.2, 0.1));

        assertSeries(output, "fieldStrength", 5_000.0);
        assertSeries(output, "electricForce", -0.01);
        assertSeries(output, "acceleration", -10_000.0);
        assertSeries(output, "transverseDisplacement", -0.08);
        assertSeries(output, "longitudinalDisplacement", 0.012);
    }

    @Test
    void binderAndParametersRejectInvalidDomainsAndNonCanonicalUnits() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, 0.0, 100.0, 0.02, 1.0, 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, 1.0, -1.0, 0.02, 1.0, 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, 1.0, 100.0, 0.0, 1.0, 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, 1.0, 100.0, 0.02, -1.0, 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new UniformElectricFieldModule.Parameters(1.0, Double.NaN, 100.0, 0.02, 1.0, 1.0));

        CanonicalQuantityBag wrongUnit = new CanonicalQuantityBag(
                Map.of("charge", bd(1.0), "mass", bd(1.0), "potential_difference", bd(100.0),
                        "plate_separation", bd(0.02), "initial_velocity", bd(1.0), "travel_time", bd(1.0)),
                Map.of("charge", "C", "mass", "g", "potential_difference", "V",
                        "plate_separation", "m", "initial_velocity", "m/s", "travel_time", "s"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));
    }

    @Test
    void rejectsNonFiniteDerivedResultsAndInvalidReferenceTime() {
        var extreme = module.bind(quantities(1.0e308, 1.0e-308, 1.0e308, 1.0e-308, 1.0, 1.0));
        assertThrows(ArithmeticException.class, () -> module.solve(extreme, new SimulationClock(0.1, 0.1)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(extreme, 0.0));

        var valid = module.bind(quantities(1.0, 1.0, 1.0, 1.0, 1.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(valid, Double.POSITIVE_INFINITY));
        assertTrue(module.solve(valid, new SimulationClock(0.1, 0.1)).values()
                .values().stream().flatMap(List::stream).allMatch(Double::isFinite));
    }

    private static void assertSeries(SolverOutput output, String key, double expected) {
        List<Double> actual = output.values().get(key);
        assertEquals(output.time().size(), actual.size(), key);
        for (double value : actual) assertEquals(expected, value, TOLERANCE, key);
    }

    private static CanonicalQuantityBag quantities(double charge, double mass,
                                                    double voltage, double separation,
                                                    double initialVelocity, double travelTime) {
        return new CanonicalQuantityBag(
                Map.of("charge", bd(charge), "mass", bd(mass), "potential_difference", bd(voltage),
                        "plate_separation", bd(separation), "initial_velocity", bd(initialVelocity),
                        "travel_time", bd(travelTime)),
                Map.of("charge", "C", "mass", "kg", "potential_difference", "V",
                        "plate_separation", "m", "initial_velocity", "m/s", "travel_time", "s"));
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
