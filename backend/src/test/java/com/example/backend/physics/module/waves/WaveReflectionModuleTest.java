package com.example.backend.physics.module.waves;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.ScalarField;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WaveReflectionModuleTest {
    private static final double TOLERANCE = 1.0e-9;
    private final WaveReflectionModule module = new WaveReflectionModule();

    @Test
    void typedModuleUsesPinnedV2IdentityAndMatchesIndependentReflectionGoldenCase() {
        assertEquals("wave_reflection", module.moduleId());
        assertEquals("wave_reflection_solver_v2", module.numericalSolverId());
        assertEquals("wave_reflection_reference_v2", module.referenceSolverId());

        var parameters = module.bind(quantities(-1.0));
        SolverOutput output = module.solve(parameters, new SimulationClock(3.0, 0.01));
        assertEquals(Set.of("displacement", "particleVelocity", "particleAcceleration"),
                output.values().keySet());
        assertEquals(Set.of("reflectionDisplacement"), output.scalarFields().keySet());
        ScalarField field = output.scalarFields().get("reflectionDisplacement");
        assertEquals(List.of(301, 401), field.shape());
        assertEquals("m", field.valueUnit());
        assertEquals("s", field.timeUnit());
        assertEquals(4.0, field.axes().getFirst().coordinates().getLast(), TOLERANCE);

        // At t=2.5 s the reflected image pulse is centered on x=2 m. The
        // direct pulse is eight widths away, so the reflected golden value is
        // isolated: R A=-0.4 m and R(-2 A c^2 / w^2)=+12.8 m/s^2.
        int reflectedCenterIndex = 250;
        assertEquals(-0.4, output.values().get("displacement").get(reflectedCenterIndex), 1.0e-7);
        assertEquals(0.0, output.values().get("particleVelocity").get(reflectedCenterIndex), 1.0e-7);
        assertEquals(12.8, output.values().get("particleAcceleration").get(reflectedCenterIndex), 1.0e-7);

        for (int index : List.of(0, 75, 150, 250, 300)) {
            Map<String, Double> oracle = module.referenceAt(parameters, output.time().get(index)).values();
            output.values().forEach((key, values) ->
                    assertEquals(values.get(index), oracle.get(key), TOLERANCE, key + " at t=" + output.time().get(index)));
        }
    }

    @Test
    void fixedAndFreeBoundariesSatisfyTheirIndependentDisplacementInvariants() {
        var fixedParameters = module.bind(quantities(-1.0, 2.0, 0.5, 1.0, 4.0, 0.0, 401.0, 4.0));
        var freeParameters = module.bind(quantities(1.0, 2.0, 0.5, 1.0, 4.0, 0.0, 401.0, 4.0));
        var fixed = module.solve(fixedParameters, new SimulationClock(1.5, 0.01));
        var free = module.solve(freeParameters, new SimulationClock(1.5, 0.01));
        ScalarField fixedField = fixed.scalarFields().get("reflectionDisplacement");
        ScalarField freeField = free.scalarFields().get("reflectionDisplacement");
        int wallArrivalIndex = 150;
        int boundaryIndex = fixedField.shape().get(1) - 1;

        // A fixed end has a displacement node. A free end adds equal incident
        // and reflected images and therefore doubles the wall displacement.
        assertEquals(0.0, fixedField.values().get(wallArrivalIndex).get(boundaryIndex), TOLERANCE);
        assertEquals(0.8, freeField.values().get(wallArrivalIndex).get(boundaryIndex), TOLERANCE);
        assertEquals(0.0, fixed.values().get("displacement").get(wallArrivalIndex), TOLERANCE);
        assertEquals(0.8, free.values().get("displacement").get(wallArrivalIndex), TOLERANCE);
        assertEquals(-25.6, free.values().get("particleAcceleration").get(wallArrivalIndex), TOLERANCE);
        assertEquals(0.0, module.referenceAt(fixedParameters, 1.5).values().get("displacement"), TOLERANCE);
        assertEquals(0.8, module.referenceAt(freeParameters, 1.5).values().get("displacement"), TOLERANCE);
    }

    @Test
    void rejectsMissingUnitsOutOfDomainValuesAndInvalidCoefficientOrSampling() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(-1.0,
                "cm", "m/s", "m", "m", "m", "m", "m", "1", "1")));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(-1.0, 4.0, 1.0, 4.1, 4.0, 0.0, 401.0, 2.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(-1.0, 4.0, 1.0, -0.1, 4.0, 0.0, 401.0, 2.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(-1.0, 4.0, 1.0, 1.0, 4.0, 0.0, 401.0, 4.1)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.01)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(-1.0, 0.0, 1.0, 1.0, 4.0, 0.0, 401.0, 2.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(-1.0, 2.0, 0.0, 1.0, 4.0, 0.0, 401.0, 2.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(-1.0, 2.0, 0.5, 1.0, 4.0, 0.0, 401.5, 2.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(-1.0, 2.0, 0.5, 1.0, 4.0, 0.0,
                        (double) ScalarField.MAX_SPATIAL_SAMPLES + 1.0, 2.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(module.bind(quantities(-1.0)), -0.1));
    }

    @Test
    void enforcesFourSamplesPerWidthAndTotalFieldResourceLimit() {
        var coarseSpace = module.bind(quantities(-1.0, 4.0, 1.0, 0.0, 4.0, 0.0, 9.0, 2.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(coarseSpace, new SimulationClock(0.5, 0.01)));

        var coarseTime = module.bind(quantities(-1.0, 4.0, 1.0, 0.0, 4.0, 0.0, 401.0, 2.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(coarseTime, new SimulationClock(0.5, 0.2)));

        var maxSpace = module.bind(quantities(-1.0, 4.0, 1.0, 0.0, 4.0, 0.0,
                (double) ScalarField.MAX_SPATIAL_SAMPLES, 2.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(maxSpace, new SimulationClock(101.0, 0.01)));
    }

    private static CanonicalQuantityBag quantities(double reflectionCoefficient) {
        return quantities(reflectionCoefficient, 2.0, 0.5, 1.0, 4.0,
                0.0, 401.0, 2.0, "m", "m/s", "m", "m", "m", "m", "1", "m", "1");
    }

    private static CanonicalQuantityBag quantities(double reflectionCoefficient, double speed, double width,
                                                   double initialPosition, double boundaryPosition,
                                                   double domainStart, double spatialSamples,
                                                   double probePosition) {
        return quantities(reflectionCoefficient, speed, width, initialPosition, boundaryPosition,
                domainStart, spatialSamples, probePosition, "m", "m/s", "m", "m", "m", "m", "1", "m", "1");
    }

    private static CanonicalQuantityBag quantities(double reflectionCoefficient, String amplitudeUnit,
                                                   String speedUnit, String widthUnit, String initialUnit,
                                                   String boundaryUnit, String domainUnit, String samplesUnit,
                                                   String probeUnit, String coefficientUnit) {
        return quantities(reflectionCoefficient, 2.0, 0.5, 1.0, 4.0,
                0.0, 401.0, 2.0, amplitudeUnit, speedUnit, widthUnit, initialUnit,
                boundaryUnit, domainUnit, samplesUnit, probeUnit, coefficientUnit);
    }

    private static CanonicalQuantityBag quantities(double reflectionCoefficient, double speed, double width,
                                                   double initialPosition, double boundaryPosition,
                                                   double domainStart, double spatialSamples, double probePosition,
                                                   String amplitudeUnit, String speedUnit, String widthUnit,
                                                   String initialUnit, String boundaryUnit, String domainUnit,
                                                   String samplesUnit, String probeUnit, String coefficientUnit) {
        Map<String, BigDecimal> values = Map.of(
                "amplitude", decimal(0.4), "wave_speed", decimal(speed), "pulse_width", decimal(width),
                "initial_position", decimal(initialPosition), "boundary_position", decimal(boundaryPosition),
                "domain_start", decimal(domainStart), "spatial_samples", decimal(spatialSamples),
                "probe_position", decimal(probePosition), "reflection_coefficient", decimal(reflectionCoefficient));
        Map<String, String> units = Map.of(
                "amplitude", amplitudeUnit, "wave_speed", speedUnit, "pulse_width", widthUnit,
                "initial_position", initialUnit, "boundary_position", boundaryUnit, "domain_start", domainUnit,
                "spatial_samples", samplesUnit, "probe_position", probeUnit,
                "reflection_coefficient", coefficientUnit);
        return new CanonicalQuantityBag(values, units);
    }

    private static BigDecimal decimal(double value) { return BigDecimal.valueOf(value); }
}
