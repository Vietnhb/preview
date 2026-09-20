package com.example.backend.physics.module.optics;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpticalInstrumentModulesTest {
    private static final double TOLERANCE = 1.0e-12;

    @Test
    void simpleMagnifierGoldenCaseMatchesIndependentReciprocalLensOracle() {
        var module = new SimpleMagnifierModule();
        var bound = new PhysicsModuleRegistry(List.of(module)).bind(
                SimpleMagnifierModule.NUMERICAL_SOLVER_ID, SimpleMagnifierModule.REFERENCE_SOLVER_ID,
                quantities(Map.of("focal_length", 0.1, "object_distance", 0.05, "near_point", 0.25)));
        var output = bound.solve(new SimulationClock(0.5, 0.5));
        assertEquals(0.1, output.scalarOutputs().get("virtualImageDistance"), TOLERANCE);
        assertEquals(2.0, output.scalarOutputs().get("linearMagnification"), TOLERANCE);
        assertEquals(2.5, output.scalarOutputs().get("relaxedAngularMagnification"), TOLERANCE);
        assertEquals(3.5, output.scalarOutputs().get("nearPointAngularMagnification"), TOLERANCE);
        var oracle = bound.reference(0.5).values();
        output.scalarOutputs().forEach((key, value) -> assertEquals(value, oracle.get(key), TOLERANCE, key));
    }

    @Test
    void simpleMagnifierRejectsFocalPlaneObjectAndAcceptsObjectApproachingLens() {
        var module = new SimpleMagnifierModule();
        assertThrows(IllegalArgumentException.class, () -> module.bind(
                quantities(Map.of("focal_length", 0.1, "object_distance", 0.1, "near_point", 0.25))));
        var nearLens = module.bind(quantities(Map.of("focal_length", 0.1, "object_distance", 1.0e-9, "near_point", 0.25)));
        assertTrue(module.solve(nearLens, new SimulationClock(0.1, 0.1))
                .scalarOutputs().get("nearPointAngularMagnification") > 1.0);
    }

    @Test
    void compoundMicroscopeGoldenCaseMatchesSeparateObjectiveAndEyepieceRatios() {
        var module = new CompoundMicroscopeModule();
        var bound = new PhysicsModuleRegistry(List.of(module)).bind(
                CompoundMicroscopeModule.NUMERICAL_SOLVER_ID, CompoundMicroscopeModule.REFERENCE_SOLVER_ID,
                quantities(Map.of("objective_focal_length", 0.005, "eyepiece_focal_length", 0.025,
                        "tube_length", 0.16, "near_point", 0.25)));
        var output = bound.solve(new SimulationClock(0.5, 0.5));
        assertEquals(32.0, output.scalarOutputs().get("objectiveMagnification"), TOLERANCE);
        assertEquals(320.0, output.scalarOutputs().get("relaxedAngularMagnification"), TOLERANCE);
        assertEquals(352.0, output.scalarOutputs().get("nearPointAngularMagnification"), TOLERANCE);
        var oracle = bound.reference(0.5).values();
        output.scalarOutputs().forEach((key, value) -> assertEquals(value, oracle.get(key), TOLERANCE, key));
    }

    @Test
    void compoundMicroscopeRejectsNonpositiveOpticalDimensions() {
        var module = new CompoundMicroscopeModule();
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(Map.of(
                "objective_focal_length", 0.0, "eyepiece_focal_length", 0.025, "tube_length", 0.16, "near_point", 0.25))));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(Map.of(
                "objective_focal_length", 0.005, "eyepiece_focal_length", 0.025, "tube_length", 0.16, "near_point", -0.25))));
    }

    @Test
    void astronomicalTelescopeGoldenCaseHasInvertedImageAndTubeLengthSum() {
        var module = new AstronomicalTelescopeModule();
        var bound = new PhysicsModuleRegistry(List.of(module)).bind(
                AstronomicalTelescopeModule.NUMERICAL_SOLVER_ID, AstronomicalTelescopeModule.REFERENCE_SOLVER_ID,
                quantities(Map.of("objective_focal_length", 1.2, "eyepiece_focal_length", 0.03)));
        var output = bound.solve(new SimulationClock(1.0, 0.5));
        assertEquals(40.0, output.scalarOutputs().get("angularMagnification"), TOLERANCE);
        assertEquals(-40.0, output.scalarOutputs().get("signedAngularMagnification"), TOLERANCE);
        assertEquals(1.23, output.scalarOutputs().get("normalAdjustmentLength"), TOLERANCE);
        var oracle = bound.reference(0.5).values();
        output.scalarOutputs().forEach((key, value) -> assertEquals(value, oracle.get(key), TOLERANCE, key));
    }

    @Test
    void astronomicalTelescopeRejectsZeroFocalLength() {
        var module = new AstronomicalTelescopeModule();
        assertThrows(IllegalArgumentException.class, () -> module.bind(
                quantities(Map.of("objective_focal_length", 1.2, "eyepiece_focal_length", 0.0))));
    }

    private static CanonicalQuantityBag quantities(Map<String, Double> values) {
        var decimals = new java.util.LinkedHashMap<String, BigDecimal>();
        var units = new java.util.LinkedHashMap<String, String>();
        values.forEach((key, value) -> {
            decimals.put(key, BigDecimal.valueOf(value));
            units.put(key, "m");
        });
        return new CanonicalQuantityBag(decimals, units);
    }
}
