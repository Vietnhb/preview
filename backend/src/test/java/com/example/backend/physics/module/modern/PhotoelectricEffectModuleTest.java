package com.example.backend.physics.module.modern;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.PhysicalConstants;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhotoelectricEffectModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private static final double ENERGY_TOLERANCE = 1.0e-30;
    private static final double ELECTRON_CHARGE = PhysicalConstants.ELEMENTARY_CHARGE;
    private final PhotoelectricEffectModule module = new PhotoelectricEffectModule();

    @Test
    void versionedTypedBindingMatchesGoldenValuesAndIndependentReference() {
        assertEquals("photoelectric_effect", module.moduleId());
        assertEquals("photoelectric_solver_v2", module.numericalSolverId());
        assertEquals("photoelectric_reference_v2", module.referenceSolverId());

        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                PhotoelectricEffectModule.NUMERICAL_SOLVER_ID,
                PhotoelectricEffectModule.REFERENCE_SOLVER_ID,
                quantities(8.0e14, 2.0e-19, ELECTRON_CHARGE));
        SolverOutput output = bound.solve(new SimulationClock(1.0, 0.5));

        assertEquals(List.of(0.0, 0.5, 1.0), output.time());
        assertEquals(Set.of("photonEnergy", "maximumKineticEnergy", "stoppingPotential",
                "wavelength", "emissionOccurs"), output.values().keySet());
        assertSeries(output, "photonEnergy", 5.30085612e-19);
        assertSeries(output, "maximumKineticEnergy", 3.30085612e-19);
        assertSeries(output, "stoppingPotential", 2.0602323426469344);
        assertSeries(output, "wavelength", 3.747405725e-7);
        assertSeries(output, "emissionOccurs", 1.0);

        for (double time : output.time()) {
            Map<String, Double> reference = bound.reference(time).values();
            assertEquals(output.values().keySet(), reference.keySet());
            output.values().forEach((key, series) ->
                    assertEquals(series.get(output.time().indexOf(time)), reference.get(key),
                            toleranceFor(key), key));
        }
    }

    @Test
    void photonEnergyScalesWithFrequencyAndRejectsAnInverseFrequencyFormula() {
        double lowFrequency = module.solve(module.bind(quantities(
                4.0e14, 0.0, ELECTRON_CHARGE)), new SimulationClock(0.1, 0.1))
                .values().get("photonEnergy").getFirst();
        double highFrequency = module.solve(module.bind(quantities(
                8.0e14, 0.0, ELECTRON_CHARGE)), new SimulationClock(0.1, 0.1))
                .values().get("photonEnergy").getFirst();

        assertEquals(2.0 * lowFrequency, highFrequency, ENERGY_TOLERANCE);
        assertEquals(5.30085612e-19, highFrequency, ENERGY_TOLERANCE);
        assertNotEquals(PhysicalConstants.PLANCK / 8.0e14, highFrequency, ENERGY_TOLERANCE);
    }

    @Test
    void emissionThresholdAndZeroWorkFunctionBoundariesAreHandled() {
        double thresholdFrequency = 1.0e15;
        double thresholdEnergy = PhysicalConstants.PLANCK * thresholdFrequency;

        SolverOutput below = module.solve(module.bind(quantities(
                Math.nextDown(thresholdFrequency), thresholdEnergy, ELECTRON_CHARGE)),
                new SimulationClock(0.2, 0.1));
        assertSeries(below, "emissionOccurs", 0.0);
        assertSeries(below, "maximumKineticEnergy", 0.0);
        assertSeries(below, "stoppingPotential", 0.0);

        var thresholdParameters = module.bind(quantities(thresholdFrequency, thresholdEnergy, ELECTRON_CHARGE));
        SolverOutput threshold = module.solve(thresholdParameters, new SimulationClock(0.2, 0.1));
        assertSeries(threshold, "emissionOccurs", 1.0);
        assertSeries(threshold, "maximumKineticEnergy", 0.0);
        assertEquals(1.0, module.referenceAt(thresholdParameters, 0.0).values().get("emissionOccurs"));

        SolverOutput zeroWork = module.solve(module.bind(quantities(1.0e15, 0.0, ELECTRON_CHARGE)),
                new SimulationClock(0.2, 0.1));
        assertSeries(zeroWork, "emissionOccurs", 1.0);
        assertTrue(zeroWork.values().get("maximumKineticEnergy").getFirst() > 0.0);
    }

    @Test
    void rejectsInvalidDomainsMissingRequiredDefaultAndNonCanonicalUnits() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.0, 0.0, ELECTRON_CHARGE)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(-1.0, 0.0, ELECTRON_CHARGE)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, -1.0, ELECTRON_CHARGE)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(1.0, 0.0, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> new PhotoelectricEffectModule.Parameters(
                1.0, Double.NaN, ELECTRON_CHARGE));

        CanonicalQuantityBag missingCharge = new CanonicalQuantityBag(
                Map.of("photon_frequency", decimal(1.0e15), "work_function", decimal(1.0e-19)),
                Map.of("photon_frequency", "Hz", "work_function", "J"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(missingCharge));

        CanonicalQuantityBag wrongUnit = new CanonicalQuantityBag(
                Map.of("photon_frequency", decimal(1.0e15), "work_function", decimal(1.0e-19),
                        "electron_charge", decimal(ELECTRON_CHARGE)),
                Map.of("photon_frequency", "kHz", "work_function", "J", "electron_charge", "C"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));
    }

    @Test
    void rejectsInvalidReferenceTimesAndUnrepresentableDerivedOutputs() {
        var valid = module.bind(quantities(1.0e15, 1.0e-19, ELECTRON_CHARGE));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(valid, Double.POSITIVE_INFINITY));

        var overflowingWavelength = module.bind(quantities(Double.MIN_VALUE, 0.0, ELECTRON_CHARGE));
        assertThrows(ArithmeticException.class,
                () -> module.solve(overflowingWavelength, new SimulationClock(0.1, 0.1)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(overflowingWavelength, 0.0));

        var overflowingPotential = module.bind(quantities(1.0e20, 0.0, Double.MIN_VALUE));
        assertThrows(ArithmeticException.class,
                () -> module.solve(overflowingPotential, new SimulationClock(0.1, 0.1)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(overflowingPotential, 0.0));
    }

    private static void assertSeries(SolverOutput output, String key, double expected) {
        List<Double> actual = output.values().get(key);
        assertEquals(output.time().size(), actual.size(), key);
        for (double value : actual) assertEquals(expected, value, toleranceFor(key), key);
    }

    private static double toleranceFor(String key) {
        return switch (key) {
            case "photonEnergy", "maximumKineticEnergy" -> ENERGY_TOLERANCE;
            default -> TOLERANCE;
        };
    }

    private static CanonicalQuantityBag quantities(double frequency, double workFunction, double charge) {
        return new CanonicalQuantityBag(
                Map.of("photon_frequency", decimal(frequency),
                        "work_function", decimal(workFunction),
                        "electron_charge", decimal(charge)),
                Map.of("photon_frequency", "Hz", "work_function", "J", "electron_charge", "C"));
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }
}
