package com.example.backend.service.simulation;

import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.LegacyPhysicsExecutionAdapterV1;
import com.example.backend.physics.reference.ReferenceSolver;
import com.example.backend.physics.reference.ReferenceSolverRegistry;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.example.backend.service.problem.CompiledSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PhysicsValidationServiceFaultInjectionTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void detectsPerturbedNumericalOutputAgainstIndependentReference() throws Exception {
        // This test-only oracle is derived independently from the deliberately corrupted
        // numerical series below: at t=0.5, the expected position is 0.25.
        ReferenceSolver independentReference = new ReferenceSolver() {
            @Override
            public String solverId() {
                return "test-independent-position-reference";
            }

            @Override
            public AnalyticalPoint solve(com.fasterxml.jackson.databind.JsonNode specification,
                    Map<String, Double> overrides, double timeSeconds) {
                return new AnalyticalPoint(Map.of("position", 0.25));
            }
        };

        SchemaDefinitionService schemaDefinitions = mock(SchemaDefinitionService.class);
        SchemaDefinitionService.SolverBinding binding = new SchemaDefinitionService.SolverBinding(
                "test-numerical", independentReference.solverId(), "1");
        SchemaVersion schema = new SchemaVersion();
        schema.setSchemaId("faulty-model");
        schema.setVersion("1");
        schema.setTopic("TEST");
        schema.setDefinition(objectMapper.readTree("""
                {
                  "validation": {
                    "tolerance": 0.000001,
                    "checkpointFractions": [0.5],
                    "outputs": {
                      "position": {
                        "absoluteTolerance": 0.000001,
                        "relativeTolerance": 0.000001
                      }
                    }
                  }
                }
                """));
        when(schemaDefinitions.requireSolverBinding("faulty-model", "1")).thenReturn(binding);
        when(schemaDefinitions.requirePublishedVersion("faulty-model", "1")).thenReturn(schema);
        when(schemaDefinitions.compiled(schema)).thenReturn(new CompiledSchema("faulty-model", "1", "TEST",
                "faulty-model", Map.of(), Map.of(), Map.of(), java.util.Set.of(), Map.of(),
                new CompiledSchema.ExecutionDefinition(1, 0.5),
                new CompiledSchema.ValidationDefinition(0.000001, List.of(0.5), Map.of("position",
                        new CompiledSchema.OutputValidationDefinition(0.000001, 0.000001, "numeric"))),
                "test-checksum"));

        PhysicsValidationService validationService = new PhysicsValidationService(
                new ReferenceSolverRegistry(List.of(independentReference)), schemaDefinitions,
                new LegacyPhysicsExecutionAdapterV1(List.of(new LegacyPhysicsExecutionAdapterV1.Permit(
                        "faulty-model", "1", "1", "test-numerical",
                        "test-independent-position-reference"))));

        // Fault injection: the independent oracle predicts 0.25 at t=0.5, while the
        // corrupted numerical result interpolates to 0.8 at the same checkpoint.
        SolverOutput perturbedNumerical = new SolverOutput(
                List.of(0.0, 1.0), Map.of(), Map.of(), Map.of(), Map.of("position", List.of(0.0, 1.6)));
        var response = validationService.validate(objectMapper.readTree("{}"), "faulty-model", "1",
                perturbedNumerical, Map.of());

        assertFalse(response.passed(), "the independent comparison must reject the injected numerical fault");
        assertEquals(1, response.checkpoints().size());
        assertEquals(0.5, response.checkpoints().getFirst().time(), 1e-12);
        assertEquals(0.8, response.checkpoints().getFirst().numerical(), 1e-12);
        assertEquals(0.25, response.checkpoints().getFirst().analytical(), 1e-12);
        assertFalse(response.checkpoints().getFirst().passed());
        assertEquals(1, response.errors().size());
        assertTrue(response.errors().getFirst().contains("expected=0.25"));
        assertTrue(response.errors().getFirst().contains("computed=0.8"));
    }
}
