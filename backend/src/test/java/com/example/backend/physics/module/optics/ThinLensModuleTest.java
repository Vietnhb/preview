package com.example.backend.physics.module.optics;

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

class ThinLensModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private final ThinLensModule module = new ThinLensModule();

    @Test
    void registeredBindingMatchesHandCalculatedConvergingLensGoldenCase() {
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                "thin_lens_solver", "thin_lens_reference", quantities(0.1, 0.3, 0.02));

        SolverOutput output = bound.solve(new SimulationClock(1.0, 0.5));
        assertEquals(List.of(0.0, 0.5, 1.0), output.time());
        assertEquals(List.of(0.15, 0.15, 0.15), output.values().get("imageDistance"));
        assertEquals(List.of(-0.01, -0.01, -0.01), output.values().get("imageHeight"));
        assertEquals(List.of(-0.5, -0.5, -0.5), output.values().get("magnification"));

        var oracle = bound.reference(0.5).values();
        assertEquals(0.15, oracle.get("imageDistance"), TOLERANCE);
        assertEquals(-0.01, oracle.get("imageHeight"), TOLERANCE);
        assertEquals(-0.5, oracle.get("magnification"), TOLERANCE);
    }

    @Test
    void focalPlaneBoundaryIsRejectedAndDivergingLensHasVirtualImage() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.1, 0.1, 0.02)));

        var parameters = module.bind(quantities(-0.1, 0.3, 0.02));
        SolverOutput output = module.solve(parameters, new SimulationClock(0.1, 0.1));
        assertEquals(-0.075, output.values().get("imageDistance").get(0), TOLERANCE);
        assertEquals(0.25, output.values().get("magnification").get(0), TOLERANCE);
        assertEquals(0.005, output.values().get("imageHeight").get(0), TOLERANCE);
        assertEquals(-0.075, module.referenceAt(parameters, 0.0).values().get("imageDistance"), TOLERANCE);
    }

    @Test
    void rejectsZeroFocalLengthNonpositiveObjectDistanceAndNoncanonicalUnits() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.0, 0.3, 0.02)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.1, 0.0, 0.02)));
        CanonicalQuantityBag wrongUnit = new CanonicalQuantityBag(
                Map.of("focal_length", bd(0.1), "object_distance", bd(0.3), "object_height", bd(0.02)),
                Map.of("focal_length", "cm", "object_distance", "m", "object_height", "m"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));
    }

    @Test
    void referenceRejectsNegativeOrNonfiniteTime() {
        var parameters = module.bind(quantities(0.1, 0.3, 0.02));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, Double.NaN));
    }

    private static CanonicalQuantityBag quantities(double focalLength, double objectDistance, double objectHeight) {
        return new CanonicalQuantityBag(
                Map.of("focal_length", bd(focalLength), "object_distance", bd(objectDistance),
                        "object_height", bd(objectHeight)),
                Map.of("focal_length", "m", "object_distance", "m", "object_height", "m"));
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
