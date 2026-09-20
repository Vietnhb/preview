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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DiffractionPolarizationModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private final DiffractionPolarizationModule module = new DiffractionPolarizationModule();

    @Test
    void registeredModuleMatchesHandCalculatedSingleSlitAndMalusGoldenCase() {
        assertEquals("diffraction_solver_v2", module.numericalSolverId());
        assertEquals("diffraction_reference_v2", module.referenceSolverId());
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                DiffractionPolarizationModule.NUMERICAL_SOLVER_ID,
                DiffractionPolarizationModule.REFERENCE_SOLVER_ID,
                quantities(500.0e-9, 1.0e-6, 1.0, 8.0, Math.PI / 3.0));

        SolverOutput output = bound.solve(new SimulationClock(0.2, 0.1));
        assertEquals(List.of(0.0, 0.1, 0.2), output.time());
        assertEquals(Set.of("diffractionAngle", "minimumExists", "transmittedIntensity"),
                output.values().keySet());
        assertSeries(output, "diffractionAngle", Math.PI / 6.0);
        assertEquals(List.of(1.0, 1.0, 1.0), output.values().get("minimumExists"));
        assertSeries(output, "transmittedIntensity", 2.0);

        Map<String, Double> reference = bound.reference(0.1).values();
        assertEquals(Math.PI / 6.0, reference.get("diffractionAngle"), TOLERANCE);
        assertEquals(1.0, reference.get("minimumExists"), TOLERANCE);
        assertEquals(2.0, reference.get("transmittedIntensity"), TOLERANCE);
    }

    @Test
    void diffractionThresholdAndUnavailableMinimumHaveExplicitBoundaryStates() {
        var threshold = module.bind(quantities(1.0e-6, 1.0e-6, 1.0, 3.0, 0.0));
        var atThreshold = module.solve(threshold, new SimulationClock(0.1, 0.1));
        assertEquals(Math.PI / 2.0, atThreshold.values().get("diffractionAngle").get(0), TOLERANCE);
        assertEquals(1.0, atThreshold.values().get("minimumExists").get(0), TOLERANCE);

        var unavailable = module.bind(quantities(1.0e-6, 1.0e-6, 2.0, 3.0, Math.PI / 2.0));
        var beyondThreshold = module.solve(unavailable, new SimulationClock(0.1, 0.1));
        assertEquals(0.0, beyondThreshold.values().get("diffractionAngle").get(0), TOLERANCE);
        assertEquals(0.0, beyondThreshold.values().get("minimumExists").get(0), TOLERANCE);
        assertEquals(0.0, beyondThreshold.values().get("transmittedIntensity").get(0), TOLERANCE);

        var zeroOrder = module.solve(module.bind(quantities(1.0e-6, 1.0e-6, 0.0, 3.0, 0.0)),
                new SimulationClock(0.1, 0.1));
        assertEquals(0.0, zeroOrder.values().get("diffractionAngle").get(0), TOLERANCE);
        assertEquals(0.0, zeroOrder.values().get("minimumExists").get(0), TOLERANCE);
        assertEquals(0.0, module.referenceAt(module.bind(quantities(1.0e-6, 1.0e-6, 0.0, 3.0, 0.0)), 0.0)
                .values().get("minimumExists"), TOLERANCE);
    }

    @Test
    void binderRejectsInvalidDomainsAndNonCanonicalUnits() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.0, 1.0, 1.0, 1.0, 0.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, 0.0, 1.0, 1.0, 0.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, 1.0, -1.0, 1.0, 0.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, 1.0, 1.5, 1.0, 0.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, 1.0, 1.0, -1.0, 0.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, 1.0, 1.0, 1.0, Double.NaN)));

        CanonicalQuantityBag wrongUnit = new CanonicalQuantityBag(
                Map.of("wavelength", bd(1.0e-6), "slit_width", bd(1.0e-6),
                        "diffraction_order", bd(1.0), "input_intensity", bd(1.0),
                        "analyzer_angle", bd(0.0)),
                Map.of("wavelength", "nm", "slit_width", "m", "diffraction_order", "1",
                        "input_intensity", "W/m2", "analyzer_angle", "rad"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));
    }

    @Test
    void referenceRejectsInvalidTimeAndMatchesPolarizationEndpoints() {
        var parameters = module.bind(quantities(1.0e-6, 2.0e-6, 1.0, 4.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(parameters, Double.POSITIVE_INFINITY));

        assertEquals(4.0, module.referenceAt(parameters, 0.0)
                .values().get("transmittedIntensity"), TOLERANCE);
        var crossed = module.bind(quantities(1.0e-6, 2.0e-6, 1.0, 4.0, Math.PI / 2.0));
        assertEquals(0.0, module.referenceAt(crossed, 0.0)
                .values().get("transmittedIntensity"), TOLERANCE);
    }

    private static CanonicalQuantityBag quantities(double wavelength, double slitWidth,
                                                    double order, double intensity,
                                                    double analyzerAngle) {
        return new CanonicalQuantityBag(
                Map.of("wavelength", bd(wavelength), "slit_width", bd(slitWidth),
                        "diffraction_order", bd(order), "input_intensity", bd(intensity),
                        "analyzer_angle", bd(analyzerAngle)),
                Map.of("wavelength", "m", "slit_width", "m", "diffraction_order", "1",
                        "input_intensity", "W/m2", "analyzer_angle", "rad"));
    }

    private static void assertSeries(SolverOutput output, String key, double expected) {
        List<Double> actual = output.values().get(key);
        assertEquals(output.time().size(), actual.size(), key);
        for (double value : actual) assertEquals(expected, value, TOLERANCE, key);
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
