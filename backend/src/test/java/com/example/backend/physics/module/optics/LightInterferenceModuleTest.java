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

class LightInterferenceModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private final LightInterferenceModule module = new LightInterferenceModule();

    @Test
    void registeredTypedPathMatchesQuarterWavelengthGoldenCaseAndIndependentOracle() {
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                LightInterferenceModule.NUMERICAL_SOLVER_ID,
                LightInterferenceModule.REFERENCE_SOLVER_ID,
                quantities(500.0e-9, 125.0e-9, 12.0));

        SolverOutput output = bound.solve(new SimulationClock(0.2, 0.1));
        assertEquals(List.of(0.0, 0.1, 0.2), output.time());
        assertEquals(Set.of("phaseDifference", "intensity"), output.values().keySet());
        assertSeries(output, "phaseDifference", Math.PI / 2.0);
        assertSeries(output, "intensity", 6.0);

        Map<String, Double> oracle = bound.reference(0.1).values();
        assertEquals(Math.PI / 2.0, oracle.get("phaseDifference"), TOLERANCE);
        assertEquals(6.0, oracle.get("intensity"), TOLERANCE);
    }

    @Test
    void goldenMaximaMinimaAndPeriodicityRespectIntensityBounds() {
        var parameters = module.bind(quantities(400.0e-9, 0.0, 9.0));
        assertEquals(9.0, module.referenceAt(parameters, 0.0).values().get("intensity"), TOLERANCE);

        var minimum = module.bind(quantities(400.0e-9, 200.0e-9, 9.0));
        assertEquals(0.0, module.solve(minimum, new SimulationClock(0.1, 0.1))
                .values().get("intensity").get(0), TOLERANCE);
        assertEquals(0.0, module.referenceAt(minimum, 0.0).values().get("intensity"), TOLERANCE);

        var onePeriod = module.bind(quantities(400.0e-9, 400.0e-9, 9.0));
        double periodicIntensity = module.solve(onePeriod, new SimulationClock(0.1, 0.1))
                .values().get("intensity").get(0);
        assertEquals(9.0, periodicIntensity, TOLERANCE);
        assertEquals(9.0, module.referenceAt(onePeriod, 0.0).values().get("intensity"), TOLERANCE);

        var general = module.bind(quantities(510.0e-9, 73.0e-9, 3.5));
        double calculated = module.referenceAt(general, 0.0).values().get("intensity");
        assertEquals(true, calculated >= 0.0 && calculated <= general.referenceIntensity());
    }

    @Test
    void changingPathDifferenceProducesTimeDependentPhaseAndIntensity() {
        double wavelength = 500.0e-9;
        var bound = new PhysicsModuleRegistry(List.of(module)).bind(
                LightInterferenceModule.NUMERICAL_SOLVER_ID,
                LightInterferenceModule.REFERENCE_SOLVER_ID,
                quantities(wavelength, 125.0e-9, 12.0, wavelength / 0.4));

        SolverOutput output = bound.solve(new SimulationClock(0.2, 0.1));
        assertSeriesValues(output, "phaseDifference", Math.PI / 2.0, Math.PI, 3.0 * Math.PI / 2.0);
        assertSeriesValues(output, "intensity", 6.0, 0.0, 6.0);

        Map<String, Double> oracleAtMiddle = bound.reference(0.1).values();
        assertEquals(Math.PI, oracleAtMiddle.get("phaseDifference"), TOLERANCE);
        assertEquals(0.0, oracleAtMiddle.get("intensity"), TOLERANCE);
    }

    @Test
    void negativePathDifferenceKeepsSignedPhaseAndEvenIntensity() {
        var bound = new PhysicsModuleRegistry(List.of(module)).bind(
                LightInterferenceModule.NUMERICAL_SOLVER_ID,
                LightInterferenceModule.REFERENCE_SOLVER_ID,
                quantities(500.0e-9, -125.0e-9, 12.0));

        SolverOutput output = bound.solve(new SimulationClock(0.2, 0.1));
        assertSeries(output, "phaseDifference", -Math.PI / 2.0);
        assertSeries(output, "intensity", 6.0);
        assertEquals(-Math.PI / 2.0, bound.reference(0.1).values().get("phaseDifference"), TOLERANCE);
        assertEquals(6.0, bound.reference(0.1).values().get("intensity"), TOLERANCE);
    }

    @Test
    void rejectsInvalidPhysicalDomainsUnitsAndReferenceTimes() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.0, 0.0, 1.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(-1.0, 0.0, 1.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, Double.NaN, 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, 0.0, 1.0, Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, 0.0, -1.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0e-320, 1.0e308, 1.0)));

        CanonicalQuantityBag wrongUnit = new CanonicalQuantityBag(
                Map.of("wavelength", bd(500.0e-9), "path_difference", bd(0.0),
                        "path_difference_rate", bd(0.0),
                        "reference_intensity", bd(1.0)),
                Map.of("wavelength", "nm", "path_difference", "m", "path_difference_rate", "m/s",
                        "reference_intensity", "W/m2"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));

        var parameters = module.bind(quantities(500.0e-9, 0.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, -1.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, Double.POSITIVE_INFINITY));
    }

    private static CanonicalQuantityBag quantities(double wavelength, double pathDifference, double intensity) {
        return quantities(wavelength, pathDifference, intensity, 0.0);
    }

    private static CanonicalQuantityBag quantities(double wavelength, double pathDifference,
                                                    double intensity, double pathDifferenceRate) {
        return new CanonicalQuantityBag(
                Map.of("wavelength", bd(wavelength), "path_difference", bd(pathDifference),
                        "path_difference_rate", bd(pathDifferenceRate),
                        "reference_intensity", bd(intensity)),
                Map.of("wavelength", "m", "path_difference", "m", "path_difference_rate", "m/s",
                        "reference_intensity", "W/m2"));
    }

    private static void assertSeriesValues(SolverOutput output, String key, double... expected) {
        List<Double> actual = output.values().get(key);
        assertEquals(expected.length, actual.size(), key);
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], actual.get(index), TOLERANCE, key + " at sample " + index);
        }
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
