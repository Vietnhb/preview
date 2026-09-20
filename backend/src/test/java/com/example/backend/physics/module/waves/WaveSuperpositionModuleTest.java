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

class WaveSuperpositionModuleTest {
    private static final double TOLERANCE = 1.0e-10;
    private final WaveSuperpositionModule module = new WaveSuperpositionModule();

    @Test
    void matchesHandCalculatedGoldenValuesAndIndependentPhasorOracle() {
        assertEquals("wave_superposition", module.moduleId());
        assertEquals("wave_superposition_solver_v2", module.numericalSolverId());
        assertEquals("wave_superposition_reference_v2", module.referenceSolverId());
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                WaveSuperpositionModule.NUMERICAL_SOLVER_ID, WaveSuperpositionModule.REFERENCE_SOLVER_ID,
                quantities(0.3, 0.4, 1.0, 2.0, 0.0, Math.PI / 2.0,
                        -1.0, 1.0, 17.0, 0.0));

        SolverOutput output = bound.solve(new SimulationClock(0.25, 0.25));
        assertEquals(List.of(0.0, 0.25), output.time());
        assertEquals(Set.of("displacement", "particleVelocity", "particleAcceleration"),
                output.values().keySet());
        assertEquals(Set.of("superpositionDisplacement"), output.scalarFields().keySet());
        ScalarField field = output.scalarFields().get("superpositionDisplacement");
        assertEquals(List.of(2, 17), field.shape());
        assertEquals("m", field.valueUnit());
        assertEquals("s", field.timeUnit());
        assertEquals(0.3, output.values().get("displacement").getFirst(), TOLERANCE);
        assertEquals(0.8 * Math.PI, output.values().get("particleVelocity").getFirst(), TOLERANCE);
        assertEquals(-1.2 * Math.PI * Math.PI,
                output.values().get("particleAcceleration").getFirst(), TOLERANCE);
        assertEquals(0.4, output.values().get("displacement").getLast(), TOLERANCE);
        assertEquals(-0.6 * Math.PI, output.values().get("particleVelocity").getLast(), TOLERANCE);
        assertEquals(-1.6 * Math.PI * Math.PI,
                output.values().get("particleAcceleration").getLast(), TOLERANCE);

        for (int index = 0; index < output.time().size(); index++) {
            int sampleIndex = index;
            Map<String, Double> oracle = bound.reference(output.time().get(index)).values();
            output.values().forEach((key, values) ->
                    assertEquals(values.get(sampleIndex), oracle.get(key), TOLERANCE, key));
        }
    }

    @Test
    void numericalFieldPreservesLinearityAndOppositePhaseCancellation() {
        SimulationClock clock = new SimulationClock(0.5, 0.125);
        var full = module.solve(module.bind(quantities(0.2, 0.35, 2.0, 3.0,
                0.3, -0.7, -1.0, 1.0, 65.0, 0.2)), clock);
        var firstOnly = module.solve(module.bind(quantities(0.2, 0.0, 2.0, 3.0,
                0.3, -0.7, -1.0, 1.0, 65.0, 0.2)), clock);
        var secondOnly = module.solve(module.bind(quantities(0.0, 0.35, 2.0, 3.0,
                0.3, -0.7, -1.0, 1.0, 65.0, 0.2)), clock);
        List<List<Double>> fullRows = full.scalarFields().get("superpositionDisplacement").values();
        List<List<Double>> firstRows = firstOnly.scalarFields().get("superpositionDisplacement").values();
        List<List<Double>> secondRows = secondOnly.scalarFields().get("superpositionDisplacement").values();
        for (int time = 0; time < fullRows.size(); time++) {
            for (int space = 0; space < fullRows.get(time).size(); space++) {
                assertEquals(firstRows.get(time).get(space) + secondRows.get(time).get(space),
                        fullRows.get(time).get(space), TOLERANCE);
            }
        }

        var cancelled = module.solve(module.bind(quantities(0.25, 0.25, 2.0, 3.0,
                0.0, Math.PI, -1.0, 1.0, 65.0, 0.0)), clock);
        assertTrue(cancelled.scalarFields().get("superpositionDisplacement").values().stream()
                .flatMap(List::stream).allMatch(value -> Math.abs(value) < 1.0e-14));
        assertTrue(cancelled.values().values().stream().flatMap(List::stream)
                .allMatch(value -> Math.abs(value) < 1.0e-12));
    }

    @Test
    void rejectsMissingValuesWrongUnitsInvalidDomainsAndBadGridResolution() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.2, 0.3, 1.0, 2.0,
                0.0, 0.0, -1.0, 1.0, 17.0, 0.0,
                "cm", "m", "Hz", "m/s", "rad", "rad", "m", "m", "1", "m")));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(-0.1, 0.3, 1.0, 2.0,
                0.0, 0.0, -1.0, 1.0, 17.0, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.2, 0.3, 0.0, 2.0,
                0.0, 0.0, -1.0, 1.0, 17.0, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.2, 0.3, 1.0, 0.0,
                0.0, 0.0, -1.0, 1.0, 17.0, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.2, 0.3, 1.0, 2.0,
                0.0, 0.0, 1.0, -1.0, 17.0, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.2, 0.3, 1.0, 2.0,
                0.0, 0.0, -1.0, 1.0, 2.5, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.2, 0.3, 1.0, 2.0,
                0.0, 0.0, -1.0, 1.0, ScalarField.MAX_SPATIAL_SAMPLES + 1.0, 0.0)));
        assertThrows(IllegalArgumentException.class, () -> module.bind(quantities(0.2, 0.3, 1.0, 2.0,
                0.0, 0.0, -1.0, 1.0, 17.0, 2.0)));

        var tooFewSpaceSamples = module.bind(quantities(0.2, 0.3, 1.0, 2.0,
                0.0, 0.0, -1.0, 1.0, 4.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(tooFewSpaceSamples, new SimulationClock(0.25, 0.125)));
        var tooFewTimeSamples = module.bind(quantities(0.2, 0.3, 4.0, 2.0,
                0.0, 0.0, -1.0, 1.0, 17.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(tooFewTimeSamples, new SimulationClock(0.25, 0.1)));
        var valid = module.bind(quantities(0.2, 0.3, 1.0, 2.0,
                0.0, 0.0, -1.0, 1.0, 17.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, Double.NaN));
    }

    @Test
    void enforcesScalarFieldResourceBoundsAndFiniteArithmetic() {
        var tooManyCells = module.bind(quantities(0.2, 0.3, 1.0, 2.0,
                0.0, 0.0, -1.0, 1.0, ScalarField.MAX_SPATIAL_SAMPLES, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(tooManyCells, new SimulationClock(1.0, 0.001)));

        var arithmeticOverflow = module.bind(quantities(0.2, 0.3, 1.0e308, 1.0e-308,
                0.0, 0.0, -1.0, 1.0, 17.0, 0.0));
        assertThrows(ArithmeticException.class,
                () -> module.solve(arithmeticOverflow, new SimulationClock(0.1, 0.01)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(arithmeticOverflow, 0.0));
    }

    private static CanonicalQuantityBag quantities(double amplitude1, double amplitude2, double frequency,
                                                   double waveSpeed, double phase1, double phase2,
                                                   double domainStart, double domainEnd, double samples,
                                                   double probePosition) {
        return quantities(amplitude1, amplitude2, frequency, waveSpeed, phase1, phase2,
                domainStart, domainEnd, samples, probePosition,
                "m", "m", "Hz", "m/s", "rad", "rad", "m", "m", "1", "m");
    }

    private static CanonicalQuantityBag quantities(double amplitude1, double amplitude2, double frequency,
                                                   double waveSpeed, double phase1, double phase2,
                                                   double domainStart, double domainEnd, double samples,
                                                   double probePosition, String amplitude1Unit,
                                                   String amplitude2Unit, String frequencyUnit, String speedUnit,
                                                   String phase1Unit, String phase2Unit, String startUnit,
                                                   String endUnit, String samplesUnit, String probeUnit) {
        Map<String, BigDecimal> values = Map.of(
                "amplitude_1", decimal(amplitude1), "amplitude_2", decimal(amplitude2),
                "frequency", decimal(frequency), "wave_speed", decimal(waveSpeed),
                "phase_1", decimal(phase1), "phase_2", decimal(phase2),
                "domain_start", decimal(domainStart), "domain_end", decimal(domainEnd),
                "spatial_samples", decimal(samples), "probe_position", decimal(probePosition));
        Map<String, String> units = Map.of(
                "amplitude_1", amplitude1Unit, "amplitude_2", amplitude2Unit,
                "frequency", frequencyUnit, "wave_speed", speedUnit,
                "phase_1", phase1Unit, "phase_2", phase2Unit,
                "domain_start", startUnit, "domain_end", endUnit,
                "spatial_samples", samplesUnit, "probe_position", probeUnit);
        return new CanonicalQuantityBag(values, units);
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }
}
