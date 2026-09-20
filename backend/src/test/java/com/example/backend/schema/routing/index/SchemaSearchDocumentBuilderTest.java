package com.example.backend.schema.routing.index;

import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.service.problem.SchemaCompiler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaSearchDocumentBuilderTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SchemaCompiler compiler = new SchemaCompiler(mapper);
    private final SchemaSearchDocumentBuilder builder = new SchemaSearchDocumentBuilder();

    @Test
    void deterministicallyProjectsCompiledContractAndApprovedMetadataOnly() throws Exception {
        var definition = mapper.readTree("""
                {
                  "version":"1.2", "topic":"KINEMATICS", "model":"uniform_acceleration",
                  "description":"Motion with constant acceleration", "learningOutcomes":["velocity change"],
                  "curriculumLabels":["grade 10"], "relationTypes":["uniform_acceleration"],
                  "endConditions":[{"type":"threshold"}],
                  "requiredQuantities":[{"key":"initial_velocity","aliases":["starting speed"],
                    "symbol":"v₀", "allowedUnits":["m/s"], "positive":false}],
                  "optionalQuantities":[{"key":"acceleration","symbols":["a", "α"],
                    "allowedUnits":["m/s^2"], "defaultValue":0, "defaultUnit":"m/s^2"}],
                  "adjustableParameters":[], "execution":{"durationSeconds":4,"stepSeconds":0.1},
                  "validation":{"tolerance":0.001,"checkpointFractions":[0.5]},
                  "output":{"probeSeries":["position"]},
                  "solverId":"internal_solver_name", "formula":"hidden_formula",
                  "visualization":{"asset":"hidden_scene_asset"}
                }
                """);
        SchemaVersion schema = new SchemaVersion();
        schema.setSchemaId("uniform-acceleration");
        schema.setVersion("1.2");
        schema.setTopic("KINEMATICS");
        schema.setName("Uniform acceleration");
        schema.setDefinition(definition);
        schema.setDefinitionChecksum(compiler.checksum(definition));

        var compiled = compiler.compile(definition, schema.getSchemaId());
        var first = builder.build(schema, compiled);
        var second = builder.build(schema, compiled);

        assertEquals(first, second);
        assertTrue(first.searchText().contains("initial_velocity"));
        assertTrue(first.searchText().contains("starting speed"));
        assertTrue(first.searchText().contains("v0"));
        assertTrue(first.searchText().contains("m/s"));
        assertTrue(first.searchText().contains("α"));
        assertTrue(first.searchText().contains("velocity change"));
        assertTrue(first.searchText().contains("uniform_acceleration"));
        assertEquals(Set.of("initial_velocity", "acceleration"), first.canonicalQuantityKeys());
        assertEquals(Set.of("starting speed"), first.aliases());
        assertEquals(Set.of("v₀", "a", "α"), first.symbols());
        assertEquals(Set.of("m/s", "m/s^2"), first.allowedUnits());
        assertEquals(Set.of("uniform_acceleration"), first.relationTypes());
        assertEquals(Set.of("threshold"), first.endConditionCapabilities());
        assertEquals(Set.of("grade 10"), first.curriculumLabels());
        assertEquals(Set.of("velocity change"), first.learningOutcomes());
        assertFalse(first.searchText().contains("hidden_formula"));
        assertFalse(first.searchText().contains("internal_solver_name"));
        assertFalse(first.searchText().contains("hidden_scene_asset"));
    }
}
