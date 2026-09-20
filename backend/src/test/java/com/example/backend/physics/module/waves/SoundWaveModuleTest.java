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

class SoundWaveModuleTest {
    private static final double TOLERANCE = 1.0e-10;
    private final SoundWaveModule module = new SoundWaveModule();

    @Test
    void typedModuleMatchesHandCalculatedPressureFieldAndIndependentProbeReference() {
        assertEquals("sound_wave_solver_v2", module.numericalSolverId());
        assertEquals("sound_wave_reference_v2", module.referenceSolverId());
        BoundPhysicsModule bound = new PhysicsModuleRegistry(List.of(module)).bind(
                SoundWaveModule.NUMERICAL_SOLVER_ID, SoundWaveModule.REFERENCE_SOLVER_ID,
                quantities(2.0, 2.0, 4.0, 0.0, 0.0, 2.0, 5.0, 1.0));

        SolverOutput output = bound.solve(new SimulationClock(0.25, 0.125));
        assertEquals(List.of(0.0, 0.125, 0.25), output.time());
        assertEquals(Set.of("pressure", "pressureRate", "pressureAcceleration"), output.values().keySet());
        assertEquals(Set.of("soundPressure"), output.scalarFields().keySet());
        List<Double> expectedFirstRow = List.of(2.0, 0.0, -2.0, 0.0, 2.0);
        List<Double> actualFirstRow = output.scalarFields().get("soundPressure").values().get(0);
        assertEquals(expectedFirstRow.size(), actualFirstRow.size());
        for (int index = 0; index < expectedFirstRow.size(); index++) {
            assertEquals(expectedFirstRow.get(index), actualFirstRow.get(index), TOLERANCE);
        }
        assertEquals("Pa", output.scalarFields().get("soundPressure").valueUnit());
        assertEquals("m", output.scalarFields().get("soundPressure").axes().getFirst().unit());

        assertEquals(-2.0, output.values().get("pressure").get(0), TOLERANCE);
        assertEquals(0.0, output.values().get("pressure").get(1), TOLERANCE);
        assertEquals(2.0, output.values().get("pressure").get(2), TOLERANCE);
        assertEquals(8.0 * Math.PI, output.values().get("pressureRate").get(1), TOLERANCE);
        assertEquals(32.0 * Math.PI * Math.PI, output.values().get("pressureAcceleration").get(0), TOLERANCE);

        for (int index = 0; index < output.time().size(); index++) {
            int sampleIndex = index;
            double time = output.time().get(index);
            Map<String, Double> reference = bound.reference(time).values();
            output.values().forEach((key, values) ->
                    assertEquals(values.get(sampleIndex), reference.get(key), TOLERANCE, key));
        }
    }

    @Test
    void defaultDomainAndSamplingFollowTheCompiledSchemaContract() {
        // These values are materialized from the pinned schema's compiled defaults.
        SoundWaveModule.Parameters parameters = module.bind(compiledDefaults(1.0, 2.0, 4.0));

        assertEquals(0.0, parameters.domainStart(), TOLERANCE);
        assertEquals(1.0, parameters.domainEnd(), TOLERANCE);
        assertEquals(2001, parameters.spatialSamples());
        assertEquals(0.0, parameters.probePosition(), TOLERANCE);
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, 2.0, 4.0, null, null, null, null, null)));
    }

    @Test
    void acceptsProbeOnDomainBoundaryAndZeroAmplitude() {
        var parameters = module.bind(quantities(0.0, 2.0, 4.0,
                0.0, 1.0, 2.0, 3.0, 2.0));
        SolverOutput output = module.solve(parameters, new SimulationClock(0.1, 0.1));
        assertEquals(List.of(0.0, 0.0), output.values().get("pressure"));
        assertEquals(0.0, output.scalarFields().get("soundPressure").values().get(0).get(2), TOLERANCE);

        var negativeDomain = module.bind(quantities(1.0, 2.0, 4.0,
                0.0, -2.0, -1.0, 9.0, -1.5));
        assertEquals(-2.0, negativeDomain.domainStart(), TOLERANCE);
        assertEquals(-1.0, negativeDomain.domainEnd(), TOLERANCE);
        assertEquals(3.0, module.solve(negativeDomain, new SimulationClock(0.25, 0.125))
                .values().get("pressure").size());
    }

    @Test
    void rejectsInvalidPhysicalDomainsUnitsAndReferenceTimes() {
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(compiledDefaults(-1.0, 2.0, 4.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(compiledDefaults(1.0, 0.0, 4.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(compiledDefaults(1.0, 2.0, -1.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, 2.0, 4.0, 0.0, 2.0, 1.0, 161.0, 2.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, 2.0, 4.0, 0.0, 0.0, 1.0, 1.5, 0.0)));
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, 2.0, 4.0, 0.0, 0.0, 1.0, 161.0, 5.0)));

        var wrongUnit = compiledDefaults(1.0, 2.0, 4.0, "kPa");
        assertThrows(IllegalArgumentException.class, () -> module.bind(wrongUnit));
        var valid = module.bind(compiledDefaults(1.0, 2.0, 4.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, -0.1));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(valid, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> module.referenceAt(valid, Double.POSITIVE_INFINITY));
    }

    @Test
    void enforcesResourceLimitsAndRejectsNonFiniteNumericalAndReferenceResults() {
        assertThrows(IllegalArgumentException.class,
                () -> module.bind(quantities(1.0, 2.0, 4.0, 0.0, 0.0, 1.0,
                        (double) ScalarField.MAX_SPATIAL_SAMPLES + 1.0, 0.0)));

        var extreme = module.bind(quantities(1.0, 1.0e308, 1.0e-308, 0.0,
                0.0, 1.0, 2.0, 0.0));
        assertThrows(ArithmeticException.class,
                () -> module.solve(extreme, new SimulationClock(0.1, 0.1)));
        assertThrows(ArithmeticException.class, () -> module.referenceAt(extreme, 0.0));
        var coarseTime = module.bind(quantities(1.0, 500.0, 343.0,
                0.0, 0.0, 1.0, 2001.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(coarseTime, new SimulationClock(0.01, 0.0006)));
        var coarseSpace = module.bind(quantities(1.0, 2.0, 1.0,
                0.0, 0.0, 1.0, 5.0, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> module.solve(coarseSpace, new SimulationClock(0.25, 0.125)));
        assertTrue(module.solve(module.bind(quantities(1.0, 2.0, 4.0,
                        0.0, 0.0, 1.0, 5.0, 0.0)), new SimulationClock(0.25, 0.125))
                .values().values().stream().flatMap(List::stream).allMatch(Double::isFinite));
    }

    private static CanonicalQuantityBag quantities(double pressure, double frequency, double speed,
                                                    Double phase, Double domainStart, Double domainEnd,
                                                    Double spatialSamples, Double probePosition) {
        return quantities(pressure, frequency, speed, phase, domainStart, domainEnd,
                spatialSamples, probePosition, "Pa");
    }

    private static CanonicalQuantityBag quantities(double pressure, double frequency, double speed,
                                                    Double phase, Double domainStart, Double domainEnd,
                                                    Double spatialSamples, Double probePosition,
                                                    String pressureUnit) {
        Map<String, BigDecimal> values = new java.util.LinkedHashMap<>();
        Map<String, String> units = new java.util.LinkedHashMap<>();
        put(values, units, "pressure_amplitude", pressure, pressureUnit);
        put(values, units, "frequency", frequency, "Hz");
        put(values, units, "sound_speed", speed, "m/s");
        put(values, units, "phase", phase, "rad");
        put(values, units, "domain_start", domainStart, "m");
        put(values, units, "domain_end", domainEnd, "m");
        put(values, units, "spatial_samples", spatialSamples, "1");
        put(values, units, "probe_position", probePosition, "m");
        return new CanonicalQuantityBag(values, units);
    }

    private static CanonicalQuantityBag compiledDefaults(double pressure, double frequency, double speed) {
        return compiledDefaults(pressure, frequency, speed, "Pa");
    }

    private static CanonicalQuantityBag compiledDefaults(double pressure, double frequency, double speed,
                                                         String pressureUnit) {
        return quantities(pressure, frequency, speed, 0.0, 0.0, 1.0, 2001.0, 0.0, pressureUnit);
    }

    private static void put(Map<String, BigDecimal> values, Map<String, String> units,
                            String key, Double value, String unit) {
        if (value == null) return;
        values.put(key, BigDecimal.valueOf(value));
        units.put(key, unit);
    }
}
