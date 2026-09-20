package com.example.backend.physics.module.waves;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.ScalarField;
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

class StandingWaveModuleTest {
    private static final double TOLERANCE = 1.0e-10;
    private final StandingWaveModule module = new StandingWaveModule();

    @Test
    void typedBindingMatchesHandCalculatedFieldProbeAndIndependentReference() {
        assertEquals("standing_wave", module.moduleId());
        assertEquals("standing_wave_solver_v2", module.numericalSolverId());
        assertEquals("standing_wave_reference_v2", module.referenceSolverId());
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                StandingWaveModule.NUMERICAL_SOLVER_ID, StandingWaveModule.REFERENCE_SOLVER_ID,
                quantities(0.2, 1.0, 2.0, 0.0, 0.0, 1.0, 5.0, 0.5));

        SolverOutput output = bound.solve(new SimulationClock(0.5, 0.25));
        assertEquals(List.of(0.0, 0.25, 0.5), output.time());
        assertEquals(Set.of("displacement", "particleVelocity", "particleAcceleration"),
                output.values().keySet());
        assertEquals(Set.of("standingDisplacement"), output.scalarFields().keySet());
        ScalarField field = output.scalarFields().get("standingDisplacement");
        assertEquals(List.of(3, 5), field.shape());
        assertEquals("m", field.valueUnit());
        assertEquals("s", field.timeUnit());
        assertEquals(List.of(0.0, 0.25, 0.5, 0.75, 1.0), field.axes().getFirst().coordinates());
        assertSeries(field.values().get(0), 0.0, Math.sqrt(0.02), 0.2, Math.sqrt(0.02), 0.0);
        assertSeries(field.values().get(1), 0.0, 0.0, 0.0, 0.0, 0.0);
        assertSeries(field.values().get(2), 0.0, -Math.sqrt(0.02), -0.2, -Math.sqrt(0.02), 0.0);
        assertSeries(output.values().get("displacement"), 0.2, 0.0, -0.2);
        assertSeries(output.values().get("particleVelocity"), 0.0, -0.4 * Math.PI, 0.0);
        assertSeries(output.values().get("particleAcceleration"),
                -0.8 * Math.PI * Math.PI, 0.0, 0.8 * Math.PI * Math.PI);

        for (int index = 0; index < output.time().size(); index++) {
            int sampleIndex = index;
            Map<String, Double> reference = bound.reference(output.time().get(index)).values();
            output.values().forEach((key, values) ->
                    assertEquals(values.get(sampleIndex), reference.get(key), TOLERANCE, key));
        }
    }

    @Test
    void zeroAmplitudeAndProbeAtEitherDomainBoundaryRemainValid() {
        var left = module.bind(quantities(0.0, 2.0, 4.0, 0.5, -1.0, 2.0, 7.0, -1.0));
        SolverOutput leftOutput = module.solve(left, new SimulationClock(0.2, 0.1));
        assertSeries(leftOutput.values().get("displacement"), 0.0, 0.0, 0.0);
        assertTrue(leftOutput.scalarFields().get("standingDisplacement").values().stream()
                .flatMap(List::stream).allMatch(value -> value == 0.0));

        var right = module.bind(quantities(0.4, 2.0, 4.0, 0.0, 1.0, 2.0, 5.0, 3.0));
        assertEquals(3.0, right.probePosition(), TOLERANCE);
        assertEquals(3.0, right.domainEnd(), TOLERANCE);
        assertEquals(0.0, module.referenceAt(right, 0.0).values().get("displacement"), TOLERANCE);
    }

    @Test
    void requiresCompiledOptionalValuesAndRejectsInvalidDomainsUnitsAndReferenceTimes() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        CanonicalQuantityBag requiredOnly = new CanonicalQuantityBag(
                Map.of("amplitude", decimal(0.2), "frequency", decimal(1.0),
                        "wave_speed", decimal(2.0), "string_length", decimal(1.0)),
                Map.of("amplitude", "m", "frequency", "Hz", "wave_speed", "m/s", "string_length", "m"));
        assertThrows(IllegalArgumentException.class, () -> module.bind(requiredOnly));

        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(-0.1, 1.0, 2.0,
                0.0, 0.0, 1.0, 5.0, 0.5)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.1, 0.0, 2.0,
                0.0, 0.0, 1.0, 5.0, 0.5)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.1, 1.0, -2.0,
                0.0, 0.0, 1.0, 5.0, 0.5)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.1, 1.0, 2.0,
                0.0, 0.0, 0.0, 5.0, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.1, 1.0, 2.0,
                0.0, 0.0, 1.0, 4.5, 0.5)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.1, 1.0, 2.0,
                0.0, 0.0, 1.0, 5.0, 1.1)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.1, 1.0, 2.0,
                0.0, 0.0, 1.0, ScalarField.MAX_SPATIAL_SAMPLES + 1.0, 0.5)));

        var wrongUnit = quantitiesWithUnits(0.2, 1.0, 2.0, 0.0, 0.0, 1.0, 5.0, 0.5,
                "cm", "Hz", "m/s", "m", "rad", "m", "1", "m");
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));

        var valid = module.bind(quantities(0.2, 1.0, 2.0, 0.0, 0.0, 1.0, 5.0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(valid, Double.POSITIVE_INFINITY));
    }

    @Test
    void enforcesFieldCellAndTimeSampleResourceLimitsAndFiniteMath() {
        var manyCells = module.bind(quantities(0.2, 1.0, 2.0,
                0.0, 0.0, 1.0, 1025.0, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(manyCells, new SimulationClock(1.0, 1.0 / 1024.0)));

        var tooManyTimes = module.bind(quantities(0.2, 0.1, 2.0,
                0.0, 0.0, 1.0, 2.0, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(tooManyTimes, new SimulationClock(16_385.0, 1.0)));

        var nonFiniteFrequency = module.bind(quantities(0.2, 1.0e154, 1.0e154,
                0.0, 0.0, 1.0, 5.0, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(nonFiniteFrequency, new SimulationClock(0.1, 0.1)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(nonFiniteFrequency, 0.0));

        SolverOutput finite = module.solve(module.bind(quantities(0.2, 1.0, 2.0,
                0.0, 0.0, 1.0, 5.0, 0.5)), new SimulationClock(0.25, 0.125));
        assertTrue(finite.scalarFields().get("standingDisplacement").values().stream()
                .flatMap(List::stream).allMatch(Double::isFinite));
    }

    private static void assertSeries(List<Double> actual, double... expected) {
        assertEquals(expected.length, actual.size());
        for (int index = 0; index < expected.length; index++) {
            assertEquals(expected[index], actual.get(index), TOLERANCE);
        }
    }

    private static CanonicalQuantityBag quantities(double amplitude, double frequency, double waveSpeed,
                                                   double phase, double domainStart, double stringLength,
                                                   double spatialSamples, double probePosition) {
        return quantitiesWithUnits(amplitude, frequency, waveSpeed, phase, domainStart, stringLength,
                spatialSamples, probePosition, "m", "Hz", "m/s", "rad", "m", "m", "1", "m");
    }

    private static CanonicalQuantityBag quantitiesWithUnits(double amplitude, double frequency, double waveSpeed,
                                                            double phase, double domainStart, double stringLength,
                                                            double spatialSamples, double probePosition,
                                                            String amplitudeUnit, String frequencyUnit,
                                                            String speedUnit, String phaseUnit, String startUnit,
                                                            String lengthUnit, String samplesUnit,
                                                            String probeUnit) {
        Map<String, BigDecimal> values = Map.of(
                "amplitude", decimal(amplitude), "frequency", decimal(frequency),
                "wave_speed", decimal(waveSpeed), "string_length", decimal(stringLength),
                "phase", decimal(phase), "domain_start", decimal(domainStart),
                "spatial_samples", decimal(spatialSamples), "probe_position", decimal(probePosition));
        Map<String, String> units = Map.of(
                "amplitude", amplitudeUnit, "frequency", frequencyUnit,
                "wave_speed", speedUnit, "string_length", lengthUnit,
                "phase", phaseUnit, "domain_start", startUnit,
                "spatial_samples", samplesUnit, "probe_position", probeUnit);
        return new CanonicalQuantityBag(values, units);
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }
}
