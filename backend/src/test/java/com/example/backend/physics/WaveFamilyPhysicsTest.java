package com.example.backend.physics;

import com.example.backend.physics.model.ScalarField;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.reference.waves.StandingWaveReferenceSolver;
import com.example.backend.physics.reference.waves.WaveReflectionReferenceSolver;
import com.example.backend.physics.reference.waves.WavePulseReferenceSolver;
import com.example.backend.physics.reference.waves.SoundWaveReferenceSolver;
import com.example.backend.physics.reference.waves.WaveSuperpositionReferenceSolver;
import com.example.backend.physics.solver.waves.StandingWaveSolver;
import com.example.backend.physics.solver.waves.WaveReflectionSolver;
import com.example.backend.physics.solver.waves.WavePulseSolver;
import com.example.backend.physics.solver.waves.SoundWaveSolver;
import com.example.backend.physics.solver.waves.WaveSuperpositionSolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveFamilyPhysicsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final WavePulseSolver pulseSolver = new WavePulseSolver();
    private final WavePulseReferenceSolver pulseReference = new WavePulseReferenceSolver();
    private final StandingWaveSolver standingSolver = new StandingWaveSolver();
    private final StandingWaveReferenceSolver standingReference = new StandingWaveReferenceSolver();
    private final WaveReflectionSolver reflectionSolver = new WaveReflectionSolver();
    private final WaveReflectionReferenceSolver reflectionReference = new WaveReflectionReferenceSolver();
    private final WaveSuperpositionSolver superpositionSolver = new WaveSuperpositionSolver();
    private final WaveSuperpositionReferenceSolver superpositionReference = new WaveSuperpositionReferenceSolver();
    private final SoundWaveSolver soundSolver = new SoundWaveSolver();
    private final SoundWaveReferenceSolver soundReference = new SoundWaveReferenceSolver();

    @Test
    void gaussianPulseTranslatesAtTheConfiguredSpeedAndMatchesReference() {
        ObjectNode specification = mapper.createObjectNode();
        specification.put("model", "wave_pulse");
        specification.put("amplitude", 0.01);
        specification.put("wave_speed", 2);
        specification.put("pulse_width", 0.2);
        specification.put("initial_position", 0.5);
        specification.put("domain_start", 0);
        specification.put("domain_end", 2);
        specification.put("probe_position", 0.5);
        specification.put("spatial_samples", 81);

        SolverOutput output = pulseSolver.solve(specification, Map.of(), 0.2, 0.05);
        ScalarField field = output.scalarFields().get("pulseDisplacement");
        assertEquals(81, field.shape().get(1));
        assertEquals(0.01, field.values().get(0).get(20), 1e-12);
        assertEquals(0.01, field.values().get(2).get(28), 1e-6); // x=.7 after t=.1
        var expected = pulseReference.solve(specification, Map.of(), 0.15);
        assertEquals(expected.values().get("displacement"), output.values().get("displacement").get(3), 1e-12);
        assertEquals(expected.values().get("particleAcceleration"), output.values().get("particleAcceleration").get(3), 1e-12);
    }

    @Test
    void standingWaveHasNodesAndMatchesIndependentReference() {
        ObjectNode specification = mapper.createObjectNode();
        specification.put("model", "standing_wave");
        specification.put("amplitude", 0.02);
        specification.put("frequency", 1);
        specification.put("wave_speed", 2);
        specification.put("string_length", 2);
        specification.put("probe_position", 0.5);
        specification.put("spatial_samples", 81);

        SolverOutput output = standingSolver.solve(specification, Map.of(), 1, 0.05);
        ScalarField field = output.scalarFields().get("standingDisplacement");
        assertEquals(0, field.values().get(0).get(0), 1e-12);
        assertEquals(0.02, field.values().get(0).get(20), 1e-12);
        assertEquals(0, output.values().get("displacement").get(5), 1e-12); // cos(2*pi*0.25)=0
        var expected = standingReference.solve(specification, Map.of(), 0.35);
        assertEquals(expected.values().get("displacement"), output.values().get("displacement").get(7), 1e-12);
        assertTrue(field.boundary().contains("standing"));
    }

    @Test
    void fixedBoundaryReflectionCancelsAtTheBoundaryAndMatchesReference() {
        ObjectNode specification = mapper.createObjectNode();
        specification.put("model", "wave_reflection");
        specification.put("amplitude", 0.01);
        specification.put("wave_speed", 2);
        specification.put("pulse_width", 0.1);
        specification.put("initial_position", 0.4);
        specification.put("boundary_position", 1.5);
        specification.put("domain_start", 0);
        specification.put("probe_position", 1.0);
        specification.put("spatial_samples", 76);
        specification.put("boundary_type", "fixed");

        SolverOutput output = reflectionSolver.solve(specification, Map.of(), 0.2, 0.05);
        ScalarField field = output.scalarFields().get("reflectionDisplacement");
        assertEquals(0, field.values().get(0).get(field.values().get(0).size() - 1), 1e-12);
        var expected = reflectionReference.solve(specification, Map.of(), 0.15);
        assertEquals(expected.values().get("displacement"), output.values().get("displacement").get(3), 1e-12);
        assertTrue(field.boundary().contains("boundary=fixed"));
    }

    @Test
    void coherentSuperpositionPreservesCancellationAndMatchesReference() {
        ObjectNode specification = mapper.createObjectNode();
        specification.put("model", "wave_superposition");
        specification.put("amplitude_1", 0.02);
        specification.put("amplitude_2", 0.02);
        specification.put("frequency", 1);
        specification.put("wave_speed", 2);
        specification.put("phase_1", 0);
        specification.put("phase_2", Math.PI);
        specification.put("domain_end", 2);
        specification.put("probe_position", 1);
        specification.put("spatial_samples", 81);

        SolverOutput output = superpositionSolver.solve(specification, Map.of(), 0.2, 0.05);
        ScalarField field = output.scalarFields().get("superpositionDisplacement");
        assertEquals(0, field.values().get(0).get(40), 1e-12);
        assertEquals(0, output.values().get("displacement").get(3), 1e-12);
        var expected = superpositionReference.solve(specification, Map.of(), 0.15);
        assertEquals(expected.values().get("particleAcceleration"), output.values().get("particleAcceleration").get(3), 1e-12);
        assertTrue(field.boundary().contains("components=2"));
    }

    @Test
    void acousticPressureFieldUsesPressureUnitsAndMatchesReference() {
        ObjectNode specification = mapper.createObjectNode();
        specification.put("model", "sound_wave");
        specification.put("pressure_amplitude", 2);
        specification.put("frequency", 2);
        specification.put("sound_speed", 4);
        specification.put("domain_end", 2);
        specification.put("probe_position", 0.5);
        specification.put("spatial_samples", 81);

        SolverOutput output = soundSolver.solve(specification, Map.of(), 0.2, 0.05);
        ScalarField field = output.scalarFields().get("soundPressure");
        assertEquals("Pa", field.valueUnit());
        var expected = soundReference.solve(specification, Map.of(), 0.15);
        assertEquals(expected.values().get("pressure"), output.values().get("pressure").get(3), 1e-12);
        assertEquals(expected.values().get("pressureAcceleration"), output.values().get("pressureAcceleration").get(3), 1e-12);
    }
}
