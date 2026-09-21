package com.example.backend.physics;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.ScalarField;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.reference.waves.StringWaveReferenceSolver;
import com.example.backend.physics.compatibility.legacy.solver.waves.StringWaveSolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StringWavePhysicsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final StringWaveSolver solver = new StringWaveSolver();
    private final StringWaveReferenceSolver reference = new StringWaveReferenceSolver();

    @Test
    void mandatoryFixtureMatchesIndependentReferenceAndParticleDerivatives() {
        ObjectNode specification = periodicFixture();
        SolverOutput output = solver.solve(specification, Map.of(), 1, 0.05);
        ScalarField field = output.scalarFields().get("transverseDisplacement");

        assertEquals(81, field.shape().get(1));
        int xHalfIndex = 10; // x=0.5 on [0,4] with dx=0.05
        assertEquals(0, field.values().get(0).get(xHalfIndex), 1e-12);
        assertEquals(0.02 * Math.PI * 4, output.values().get("particleVelocity").get(0), 1e-12);

        int xZeroIndex = 0;
        assertEquals(0.02, field.values().get(0).get(xZeroIndex), 1e-12);
        // The configured probe is x=0.5, where u=0 and therefore a=0.
        assertEquals(0, output.values().get("particleAcceleration").get(0), 1e-12);

        // The mandatory fixture also checks the stated x=0 checkpoint.
        ObjectNode originProbe = periodicFixture();
        originProbe.put("probe_position", 0);
        AnalyticalPoint origin = reference.solve(originProbe, Map.of(), 0);
        assertEquals(-0.02 * Math.pow(4 * Math.PI, 2), origin.values().get("particleAcceleration"), 1e-12);

        AnalyticalPoint expected = reference.solve(specification, Map.of(), 0.4);
        int timeIndex = 8;
        assertEquals(expected.values().get("displacement"), output.values().get("displacement").get(timeIndex), 1e-12);
        assertEquals(expected.values().get("particleVelocity"), output.values().get("particleVelocity").get(timeIndex), 1e-12);
        assertEquals(expected.values().get("particleAcceleration"), output.values().get("particleAcceleration").get(timeIndex), 1e-12);

        // The same versioned field must survive the JSON shape used by SimulationRun replay.
        ObjectNode persisted = mapper.createObjectNode();
        persisted.set("scalarFields", mapper.valueToTree(output.scalarFields()));
        Map<String, ScalarField> replayed = mapper.convertValue(persisted.get("scalarFields"),
                mapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, ScalarField.class));
        assertEquals(field.shape(), replayed.get("transverseDisplacement").shape());
        assertEquals(field.values().get(8).get(10), replayed.get("transverseDisplacement").values().get(8).get(10), 1e-12);
    }

    @Test
    void sourceStartedWaveLeavesPointsAheadOfFrontUndisturbed() {
        ObjectNode specification = periodicFixture();
        specification.put("source_mode", "source_started");
        specification.put("source_ramp_seconds", 0.1);
        SolverOutput output = solver.solve(specification, Map.of(), 0.1, 0.05);
        ScalarField field = output.scalarFields().get("transverseDisplacement");
        int xAheadIndex = 20; // x=1.0 while c*t=0.4
        assertEquals(0, field.values().get(2).get(xAheadIndex), 1e-12);
        assertEquals(0, output.values().get("displacement").get(0), 1e-12);
    }

    @Test
    void scalarFieldRejectsShapeAndSamplingViolations() {
        assertThrows(IllegalArgumentException.class, () -> new ScalarField(
                1, "scalarField", 1,
                java.util.List.of(new ScalarField.Axis("x", "m", java.util.List.of(0d, 0.5d))),
                java.util.List.of(2, 2), java.util.List.of(0d, 0.1d),
                java.util.List.of(java.util.List.of(0d, 1d), java.util.List.of(0d)),
                "m", "s", new ScalarField.Sampling(0.5, 0.1), "linear", "open"));
        assertTrue(ScalarField.MAX_CELLS > 0);
    }

    private ObjectNode periodicFixture() {
        ObjectNode specification = mapper.createObjectNode();
        specification.put("model", "string_wave");
        specification.put("amplitude", 0.02);
        specification.put("frequency", 2);
        specification.put("wave_speed", 4);
        specification.put("phase", 0);
        specification.put("domain_start", 0);
        specification.put("domain_end", 4);
        specification.put("probe_position", 0.5);
        specification.put("spatial_samples", 81);
        return specification;
    }
}
