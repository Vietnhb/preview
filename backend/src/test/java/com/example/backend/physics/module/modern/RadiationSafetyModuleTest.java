package com.example.backend.physics.module.modern;

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

class RadiationSafetyModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private final RadiationSafetyModule module = new RadiationSafetyModule();

    @Test
    void typedBindingMatchesHandCalculatedInverseSquareValuesAndIndependentReference() {
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                RadiationSafetyModule.NUMERICAL_SOLVER_ID,
                RadiationSafetyModule.REFERENCE_SOLVER_ID,
                quantities(4.0, 1.0, 2.0));
        SolverOutput output = bound.solve(new SimulationClock(1.0, 0.5));

        assertEquals("radiation_safety_solver_v2", module.numericalSolverId());
        assertEquals("radiation_safety_reference_v2", module.referenceSolverId());
        assertEquals(List.of(0.0, 0.5, 1.0), output.time());
        assertEquals(Map.of(), output.values());
        assertEquals(Map.of("doseRate", 1.0), output.scalarOutputs());
        for (double time : output.time()) {
            assertEquals(1.0, bound.reference(time).values().get("doseRate"), TOLERANCE);
        }

        SolverOutput twiceAsFar = module.solve(module.bind(quantities(4.0, 1.0, 4.0)),
                new SimulationClock(0.2, 0.1));
        assertEquals(0.25, twiceAsFar.scalarOutputs().get("doseRate"), TOLERANCE);
        assertEquals(0.25, module.referenceAt(module.bind(quantities(4.0, 1.0, 4.0)), 0.0)
                .values().get("doseRate"), TOLERANCE);
    }

    @Test
    void adjustableDistanceLimitsAndZeroSourceBoundaryAreFinite() {
        SolverOutput nearest = module.solve(module.bind(quantities(4.0, 1.0, 0.001)),
                new SimulationClock(0.1, 0.1));
        SolverOutput farthest = module.solve(module.bind(quantities(4.0, 1.0, 1000.0)),
                new SimulationClock(0.1, 0.1));
        assertEquals(4_000_000.0, nearest.scalarOutputs().get("doseRate"), 1.0e-8);
        assertEquals(4.0e-6, farthest.scalarOutputs().get("doseRate"), 1.0e-18);

        SolverOutput zeroSource = module.solve(module.bind(quantities(0.0, 1.0, Double.MIN_VALUE)),
                new SimulationClock(0.1, 0.1));
        assertEquals(0.0, zeroSource.scalarOutputs().get("doseRate"), TOLERANCE);
        assertEquals(0.0, module.referenceAt(module.bind(
                quantities(0.0, 1.0, Double.MIN_VALUE)), 0.0).values().get("doseRate"));
    }

    @Test
    void rejectsMissingValuesWrongUnitsAndInvalidPhysicalDomains() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(-1.0, 1.0, 2.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, 0.0, 2.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, 1.0, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, -1.0, 2.0)));
        assertThrows(IllegalArgumentException.class,
                () -> new RadiationSafetyModule.Parameters(Double.NaN, 1.0, 2.0));
        assertThrows(IllegalArgumentException.class,
                () -> new RadiationSafetyModule.Parameters(1.0, Double.POSITIVE_INFINITY, 2.0));

        CanonicalQuantityBag wrongUnit = new CanonicalQuantityBag(
                Map.of("reference_dose_rate", bd(4.0), "reference_distance", bd(1.0), "distance", bd(2.0)),
                Map.of("reference_dose_rate", "mGy/s", "reference_distance", "m", "distance", "m"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));
    }

    @Test
    void rejectsUnrepresentableDoseAndKeepsSamplingResourceBounded() {
        var overflowing = module.bind(quantities(4.0, 1.0, 1.0e-308));
        assertThrows(ArithmeticException.class,
                () -> module.solve(overflowing, new SimulationClock(0.1, 0.1)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(overflowing, 0.0));

        SolverOutput bounded = module.solve(module.bind(quantities(4.0, 1.0, 2.0)),
                new SimulationClock(1.0, 1.0e-12));
        assertEquals(16_385, bounded.time().size());
        assertEquals(1, bounded.scalarOutputs().size());
        assertTrue(Double.isFinite(bounded.scalarOutputs().get("doseRate")));
    }

    private static CanonicalQuantityBag quantities(double doseRate, double referenceDistance, double distance) {
        return new CanonicalQuantityBag(
                Map.of("reference_dose_rate", bd(doseRate),
                        "reference_distance", bd(referenceDistance), "distance", bd(distance)),
                Map.of("reference_dose_rate", "Gy/s", "reference_distance", "m", "distance", "m"));
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
