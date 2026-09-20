package com.example.backend.physics.module.waves;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.ScalarField;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringWaveModuleTest {
    private static final double TOLERANCE = 1.0e-10;
    private final StringWaveModule module = new StringWaveModule();

    @Test
    void typedPeriodicWaveMatchesHandGoldenValuesAndCycleBasedOracle() {
        assertEquals("string_wave", module.moduleId());
        assertEquals("string_wave_solver_v2", module.numericalSolverId());
        assertEquals("string_wave_reference_v2", module.referenceSolverId());

        StringWaveModule.Parameters parameters = module.bind(quantities(
                0.02, 2.0, 4.0, 0.0, 4.0, 17.0, 0.5));
        SolverOutput output = module.solve(parameters, new SimulationClock(0.5, 0.125));
        ScalarField field = output.scalarFields().get("transverseDisplacement");
        assertEquals(5, field.shape().get(0));
        assertEquals(17, field.shape().get(1));
        assertEquals("m", field.valueUnit());
        assertEquals("s", field.timeUnit());
        assertEquals(4.0, field.axes().getFirst().coordinates().getLast(), TOLERANCE);

        // At x=0, t=0 the field has u=A. The probe is at lambda/4, where
        // the independent phase checkpoint gives u=0, v=A(2 pi f), and a=0.
        assertEquals(0.02, field.values().getFirst().getFirst(), TOLERANCE);
        assertEquals(0.0,
                output.values().get("particleAcceleration").getFirst(), TOLERANCE);
        assertEquals(0.0, output.values().get("displacement").getFirst(), TOLERANCE);
        assertEquals(0.08 * Math.PI, output.values().get("particleVelocity").getFirst(), TOLERANCE);
        assertEquals(0.02, output.values().get("displacement").get(1), TOLERANCE);
        assertEquals(-0.02 * Math.pow(4.0 * Math.PI, 2),
                output.values().get("particleAcceleration").get(1), TOLERANCE);
        assertEquals(0.0, output.values().get("particleVelocity").get(1), TOLERANCE);

        for (int index = 0; index < output.time().size(); index++) {
            int sample = index;
            Map<String, Double> oracle = module.referenceAt(parameters, output.time().get(index)).values();
            output.values().forEach((key, values) ->
                    assertEquals(values.get(sample), oracle.get(key), TOLERANCE, key + " at sample " + sample));
        }
    }

    @Test
    void fieldSatisfiesPeriodicityAndPropagationDirectionInvariants() {
        StringWaveModule.Parameters parameters = module.bind(quantities(
                0.02, 2.0, 4.0, 0.3, 4.0, 17.0, 0.0));
        SolverOutput output = module.solve(parameters, new SimulationClock(0.5, 0.125));
        ScalarField field = output.scalarFields().get("transverseDisplacement");

        // One wavelength is eight spatial intervals; one period is four time intervals.
        for (int time = 0; time < field.values().size(); time++) {
            for (int x = 0; x <= 8; x++) {
                assertEquals(field.values().get(time).get(x), field.values().get(time).get(x + 8), TOLERANCE);
            }
        }
        for (int x = 0; x < field.values().getFirst().size(); x++) {
            assertEquals(field.values().getFirst().get(x), field.values().get(4).get(x), TOLERANCE);
        }
        // A feature advances c*dt=0.5 m in +x during the first 0.125 seconds.
        assertEquals(field.values().getFirst().get(0), field.values().get(1).get(2), TOLERANCE);
    }

    @Test
    void zeroAmplitudeIsAValidZeroFieldBoundaryCase() {
        StringWaveModule.Parameters parameters = module.bind(quantities(
                0.0, 2.0, 4.0, 0.0, 4.0, 17.0, 2.0));
        SolverOutput output = module.solve(parameters, new SimulationClock(0.5, 0.125));
        assertTrue(output.scalarFields().get("transverseDisplacement").values().stream()
                .flatMap(java.util.List::stream).allMatch(value -> value == 0.0));
        assertTrue(output.values().values().stream().flatMap(java.util.List::stream)
                .allMatch(value -> value == 0.0));
    }

    @Test
    void acceptsInclusivePhysicalAndSamplingBoundaries() {
        StringWaveModule.Parameters parameters = module.bind(quantities(
                1.0, 5.0, 1.0, -2.0 * Math.PI, 0.1, 5.0, 0.1));
        SolverOutput output = module.solve(parameters, new SimulationClock(0.2, 0.05));
        ScalarField field = output.scalarFields().get("transverseDisplacement");
        assertEquals(5, field.shape().get(0));
        assertEquals(5, field.shape().get(1));
        assertEquals(1.0, field.values().getFirst().getFirst(), TOLERANCE);
        assertEquals(-1.0, field.values().getFirst().getLast(), TOLERANCE);
        assertEquals(-1.0, output.values().get("displacement").getFirst(), TOLERANCE);
    }

    @Test
    void rejectsMissingCanonicalInputsWrongUnitsAndInvalidPhysicalDomains() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(
                0.02, 2.0, 4.0, 0.0, 4.0, 17.0, 0.5,
                "cm", "Hz", "m/s", "rad", "m", "1", "m")));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(-0.1, 2.0, 4.0, 0.0, 4.0, 17.0, 0.5)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.02, 0.0, 4.0, 0.0, 4.0, 17.0, 0.5)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.02, 2.0, 0.0, 0.0, 4.0, 17.0, 0.5)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.02, 2.0, 4.0, 7.0, 4.0, 17.0, 0.5)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.02, 2.0, 4.0, 0.0, 0.0, 17.0, 0.5)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.02, 2.0, 4.0, 0.0, 4.0, 2.5, 0.5)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.02, 2.0, 4.0, 0.0, 4.0,
                        ScalarField.MAX_SPATIAL_SAMPLES + 1.0, 0.5)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.02, 2.0, 4.0, 0.0, 4.0, 17.0, 4.1)));
    }

    @Test
    void rejectsUnderResolvedGridsOversizedFieldsAndNegativeReferenceTime() {
        StringWaveModule.Parameters coarseSpace = module.bind(quantities(
                0.02, 2.0, 1.0, 0.0, 4.0, 17.0, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(coarseSpace, new SimulationClock(0.5, 0.01)));

        StringWaveModule.Parameters coarseTime = module.bind(quantities(
                0.02, 2.0, 4.0, 0.0, 4.0, 17.0, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(coarseTime, new SimulationClock(0.5, 0.2)));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(coarseTime, -0.01));

        StringWaveModule.Parameters resourceLimit = module.bind(quantities(
                0.02, 2.0, 4.0, 0.0, 20.0, (double) ScalarField.MAX_SPATIAL_SAMPLES, 0.5));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(resourceLimit, new SimulationClock(10.0, 0.01)));
    }

    private static CanonicalQuantityBag quantities(double amplitude, double frequency, double waveSpeed,
                                                   double phase, double length, double spatialSamples,
                                                   double probePosition) {
        return quantities(amplitude, frequency, waveSpeed, phase, length, spatialSamples, probePosition,
                "m", "Hz", "m/s", "rad", "m", "1", "m");
    }

    private static CanonicalQuantityBag quantities(double amplitude, double frequency, double waveSpeed,
                                                   double phase, double length, double spatialSamples,
                                                   double probePosition, String amplitudeUnit,
                                                   String frequencyUnit, String speedUnit, String phaseUnit,
                                                   String lengthUnit, String samplesUnit, String probeUnit) {
        Map<String, BigDecimal> values = Map.of(
                "amplitude", decimal(amplitude), "frequency", decimal(frequency),
                "wave_speed", decimal(waveSpeed), "phase", decimal(phase),
                "domain_length", decimal(length), "spatial_samples", decimal(spatialSamples),
                "probe_position", decimal(probePosition));
        Map<String, String> units = Map.of(
                "amplitude", amplitudeUnit, "frequency", frequencyUnit, "wave_speed", speedUnit,
                "phase", phaseUnit, "domain_length", lengthUnit,
                "spatial_samples", samplesUnit, "probe_position", probeUnit);
        return new CanonicalQuantityBag(values, units);
    }

    private static BigDecimal decimal(double value) { return BigDecimal.valueOf(value); }
}
