package com.example.backend.service.problem;

import com.example.backend.ai.normalization.UnitNormalizer;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.exception.SchemaCompilationException;
import com.example.backend.repository.curriculum.TopicRepository;
import com.example.backend.repository.problem.SchemaVersionRepository;
import com.example.backend.repository.simulation.SolverVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class SchemaCompilerIdentityTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SchemaCompiler compiler = new SchemaCompiler(mapper, new UnitNormalizer(mapper));

    @Test
    void productionCompileRequiresIdentityFromTheSchemaVersion() throws Exception {
        JsonNode definition = mapper.readTree("""
                {
                  "model": "identity-fixture",
                  "requiredQuantities": [],
                  "optionalQuantities": [],
                  "adjustableParameters": [],
                  "execution": {"durationSeconds": 1, "stepSeconds": 0.1},
                  "validation": {"tolerance": 0.000001, "checkpointFractions": [0.25, 0.5, 0.75, 1]},
                  "output": {"probeSeries": []}
                }
                """);

        var compiled = compiler.compile(definition, "identity-fixture", "2.3", "KINEMATICS");

        assertEquals("identity-fixture", compiled.schemaId());
        assertEquals("2.3", compiled.version());
        assertEquals("KINEMATICS", compiled.topic());

    }

    @Test
    void productionCompileRejectsMissingVersionOrTopicIdentity() throws Exception {
        JsonNode definition = mapper.readTree("""
                {
                  "model": "identity-fixture",
                  "requiredQuantities": [],
                  "adjustableParameters": [],
                  "execution": {"durationSeconds": 1, "stepSeconds": 0.1},
                  "validation": {"tolerance": 0.000001, "checkpointFractions": [0.25, 0.5, 0.75, 1]},
                  "output": {"probeSeries": []}
                }
                """);

        assertThrows(SchemaCompilationException.class,
                () -> compiler.compile(definition, "identity-fixture", "", "KINEMATICS"));
        assertThrows(SchemaCompilationException.class,
                () -> compiler.compile(definition, "identity-fixture", "1.0", " "));
    }

    @Test
    void runtimeCompilationTakesIdentityFromPersistedSchemaVersion() throws Exception {
        SchemaDefinitionService service = new SchemaDefinitionService(
                mock(SchemaVersionRepository.class), mock(SolverVersionRepository.class), mock(TopicRepository.class));
        JsonNode definition = mapper.readTree("""
                {
                  "model": "identity-fixture",
                  "requiredQuantities": [],
                  "optionalQuantities": [],
                  "adjustableParameters": [],
                  "execution": {"durationSeconds": 1, "stepSeconds": 0.1},
                  "validation": {"tolerance": 0.000001, "checkpointFractions": [0.25, 0.5, 0.75, 1]},
                  "output": {"probeSeries": []}
                }
                """);
        SchemaVersion persisted = new SchemaVersion();
        persisted.setSchemaId("identity-fixture");
        persisted.setVersion("2.3");
        persisted.setTopic("KINEMATICS");
        persisted.setDefinition(definition);

        var compiled = service.compiled(persisted);

        assertEquals("identity-fixture", compiled.schemaId());
        assertEquals("2.3", compiled.version());
        assertEquals("KINEMATICS", compiled.topic());

        persisted.setDefinitionChecksum("stale-checksum");
        assertThrows(SchemaCompilationException.class, () -> service.compiled(persisted));
    }
}
