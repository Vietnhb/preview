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

class WavePulseModuleTest {
    private static final double TOLERANCE = 1.0e-10;
    private final WavePulseModule module = new WavePulseModule();

    @Test
    void typedWavePulseMatchesIndependentGoldenValuesAndReference() {
        assertEquals("wave_pulse", module.moduleId());
        assertEquals("wave_pulse_solver_v2", module.numericalSolverId());
        assertEquals("wave_pulse_reference_v2", module.referenceSolverId());
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                WavePulseModule.NUMERICAL_SOLVER_ID, WavePulseModule.REFERENCE_SOLVER_ID,
                quantities(0.4, 2.0, 0.5, 1.0, -1.0, 3.0, 33.0, 1.0));

        SolverOutput output = bound.solve(new SimulationClock(0.25, 0.0625));
        assertEquals(List.of(0.0, 0.0625, 0.125, 0.1875, 0.25), output.time());
        assertEquals(Set.of("displacement", "particleVelocity", "particleAcceleration"),
                output.values().keySet());
        assertEquals(Set.of("pulseDisplacement"), output.scalarFields().keySet());
        ScalarField field = output.scalarFields().get("pulseDisplacement");
        assertEquals(List.of(5, 33), field.shape());
        assertEquals("m", field.valueUnit());
        assertEquals("s", field.timeUnit());

        // At t=0 the probe is at the center. At t=.25 the center has moved
        // one width past the probe, so q=-1 and exp(-q^2)=1/e.
        assertEquals(0.4, output.values().get("displacement").getFirst(), TOLERANCE);
        assertEquals(0.0, output.values().get("particleVelocity").getFirst(), TOLERANCE);
        assertEquals(-12.8, output.values().get("particleAcceleration").getFirst(), TOLERANCE);
        assertEquals(0.4 / Math.E, output.values().get("displacement").getLast(), TOLERANCE);
        assertEquals(-3.2 / Math.E, output.values().get("particleVelocity").getLast(), TOLERANCE);
        assertEquals(12.8 / Math.E, output.values().get("particleAcceleration").getLast(), TOLERANCE);

        for (int index = 0; index < output.time().size(); index++) {
            int sampleIndex = index;
            Map<String, Double> oracle = bound.reference(output.time().get(index)).values();
            output.values().forEach((key, values) ->
                    assertEquals(values.get(sampleIndex), oracle.get(key), TOLERANCE, key));
        }
    }

    @Test
    void translatedPulsePreservesItsShapeAndProbeSeriesStayFinite() {
        var parameters = module.bind(quantities(0.8, 2.0, 0.5, 0.0,
                -1.0, 2.0, 25.0, 0.0));
        SolverOutput output = module.solve(parameters, new SimulationClock(0.0625, 0.0625));
        List<List<Double>> rows = output.scalarFields().get("pulseDisplacement").values();
        assertEquals(2, rows.size());
        // dx=c*dt=.125, so this one-cell translation is an advection invariant.
        for (int index = 1; index < rows.get(1).size(); index++) {
            assertEquals(rows.get(0).get(index - 1), rows.get(1).get(index), TOLERANCE);
        }
        assertTrue(output.values().values().stream().flatMap(List::stream).allMatch(Double::isFinite));
        assertTrue(rows.stream().flatMap(List::stream).allMatch(Double::isFinite));
    }

    @Test
    void rejectsInvalidInputsUnitsAndReferenceTimes() {
        assertThrows(IllegalArgumentException.class, () -> module.bind(CanonicalQuantityBag.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(-0.1, 2.0, 0.5, 1.0, -1.0, 3.0, 33.0, 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.4, 0.0, 0.5, 1.0, -1.0, 3.0, 33.0, 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.4, 2.0, -0.5, 1.0, -1.0, 3.0, 33.0, 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.4, 2.0, 0.5, 4.0, -1.0, 3.0, 33.0, 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.4, 2.0, 0.5, 1.0, -1.0, 0.5, 33.0, 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.4, 2.0, 0.5, 1.0, -1.0, 3.0, 32.5, 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.4, 2.0, 0.5, 1.0, -1.0, 3.0,
                        (double) ScalarField.MAX_SPATIAL_SAMPLES + 1.0, 1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(0.4, 2.0, 0.5, 1.0, -1.0, 3.0, 33.0, 4.0)));

        var wrongUnit = quantities(0.4, 2.0, 0.5, 1.0, -1.0, 3.0, 33.0, 1.0,
                "cm", "m/s", "m", "m", "m", "m", "1", "m");
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));
        var valid = module.bind(quantities(0.4, 2.0, 0.5, 1.0, -1.0, 3.0, 33.0, 1.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(valid, Double.POSITIVE_INFINITY));
    }

    @Test
    void enforcesGridResourceAndArithmeticLimits() {
        var coarseSpace = module.bind(quantities(0.4, 2.0, 0.5, 1.0,
                -1.0, 3.0, 9.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(coarseSpace, new SimulationClock(0.25, 0.0625)));

        var coarseTime = module.bind(quantities(0.4, 2.0, 0.5, 1.0,
                -1.0, 3.0, 33.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(coarseTime, new SimulationClock(0.25, 0.1)));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(coarseTime, new SimulationClock(16_385.0, 1.0)));

        var tooManyCells = module.bind(quantities(0.4, 2.0, 0.5, 1.0,
                -1.0, 3.0, 1025.0, 1.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(tooManyCells, new SimulationClock(1.0, 0.001)));

        var overflowHorizon = module.bind(quantities(0.4, 1.0e308, 0.5, 1.0,
                -1.0, 3.0, 33.0, 1.0));
        assertThrows(ArithmeticException.class,
                () -> module.solve(overflowHorizon, new SimulationClock(10.0, 0.0625)));
        var extreme = module.bind(quantities(0.4, 1.0e308, 1.0e-308, 1.0,
                -1.0, 3.0, 33.0, 1.0));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(extreme, 0.0));
    }

    private static CanonicalQuantityBag quantities(double amplitude, double waveSpeed, double width,
                                                    double initialPosition, double domainStart,
                                                    double domainEnd, double spatialSamples,
                                                    double probePosition) {
        return quantities(amplitude, waveSpeed, width, initialPosition, domainStart, domainEnd,
                spatialSamples, probePosition, "m", "m/s", "m", "m", "m", "m", "1", "m");
    }

    private static CanonicalQuantityBag quantities(double amplitude, double waveSpeed, double width,
                                                    double initialPosition, double domainStart,
                                                    double domainEnd, double spatialSamples,
                                                    double probePosition, String amplitudeUnit,
                                                    String speedUnit, String widthUnit, String initialUnit,
                                                    String startUnit, String endUnit, String samplesUnit,
                                                    String probeUnit) {
        Map<String, BigDecimal> values = Map.of(
                "amplitude", decimal(amplitude), "wave_speed", decimal(waveSpeed),
                "pulse_width", decimal(width), "initial_position", decimal(initialPosition),
                "domain_start", decimal(domainStart), "domain_end", decimal(domainEnd),
                "spatial_samples", decimal(spatialSamples), "probe_position", decimal(probePosition));
        Map<String, String> units = Map.of(
                "amplitude", amplitudeUnit, "wave_speed", speedUnit, "pulse_width", widthUnit,
                "initial_position", initialUnit, "domain_start", startUnit, "domain_end", endUnit,
                "spatial_samples", samplesUnit, "probe_position", probeUnit);
        return new CanonicalQuantityBag(values, units);
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }
}
