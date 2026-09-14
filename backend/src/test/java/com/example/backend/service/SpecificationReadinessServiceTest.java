package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.backend.entity.AmbiguityCase;
import com.example.backend.entity.AmbiguityStatus;
import com.example.backend.entity.ConfirmationState;
import com.example.backend.entity.SchemaVersion;
import com.example.backend.entity.Specification;
import com.fasterxml.jackson.databind.ObjectMapper;

class SpecificationReadinessServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void canonicalizesRequiredFieldsAndMergesDuplicateAiCodes() throws Exception {
        SchemaDefinitionService schemas = mock(SchemaDefinitionService.class);
        SchemaVersion schema = new SchemaVersion();
        schema.setDefinition(objectMapper.readTree("""
                {"model":"elastic_collision_1d","requiredQuantities":[
                  {"key":"initial_position_2","aliases":["x2"]},
                  {"key":"velocity_1","aliases":["v1"]},
                  {"key":"velocity_2","aliases":["v2"]}]}
                """));
        when(schemas.requireApproved("dynamics_collision", "1.7")).thenReturn(schema);
        when(schemas.missingRequiredQuantities(any(), eq(schema.getDefinition()))).thenReturn(List.of(
                new SchemaDefinitionService.RequiredGap("initial_position_2", "m"),
                new SchemaDefinitionService.RequiredGap("velocity_1", "m/s"),
                new SchemaDefinitionService.RequiredGap("velocity_2", "m/s")));

        Specification specification = new Specification();
        specification.setSchemaId("dynamics_collision"); specification.setSchemaVersion("1.7");
        specification.setContractVersion("1.0"); specification.setTopic("DYNAMICS");
        specification.setObjects(objectMapper.createArrayNode());
        specification.setQuantities(objectMapper.readTree("""
                [{"name":"initial_position_2","normalizedValue":0,"normalizedUnit":"m"},
                 {"name":"velocity_1","normalizedValue":0,"normalizedUnit":"m/s"},
                 {"name":"velocity_2","normalizedValue":0,"normalizedUnit":"m/s"},
                 {"name":"mass_1","normalizedValue":2,"normalizedUnit":"kg"}]
                """));
        specification.setRelations(objectMapper.createArrayNode()); specification.setAmbiguity(objectMapper.createArrayNode());
        specification.setConfirmationState(ConfirmationState.UNRESOLVED);
        specification.addAmbiguityCase(ambiguity("missing_required_quantity", "objects.object_2.initial_position", "Position?"));
        specification.addAmbiguityCase(ambiguity("missing_required_quantity", "objects.object_1.initial_velocity", "Velocity 1?"));
        specification.addAmbiguityCase(ambiguity("missing_required_quantity", "objects.object_2.initial_velocity", "Velocity 2?"));

        new SpecificationReadinessService(schemas, objectMapper).ensureRequiredAmbiguities(specification);

        List<AmbiguityCase> open = specification.getAmbiguityCases().stream()
                .filter(item -> item.getStatus() == AmbiguityStatus.OPEN).toList();
        assertThat(open).extracting(AmbiguityCase::getCode).containsExactlyInAnyOrder(
                "schema.required.initial_position_2", "schema.required.velocity_1", "schema.required.velocity_2");
        assertThat(open).extracting(AmbiguityCase::getQuestion)
                .containsExactlyInAnyOrder("Position?", "Velocity 1?", "Velocity 2?");
        assertThat(specification.getAmbiguity()).hasSize(3);
        List<String> quantityNames = new java.util.ArrayList<>();
        specification.getQuantities().forEach(item -> quantityNames.add(item.path("name").asText()));
        assertThat(quantityNames).containsExactly("mass_1");
    }

    private AmbiguityCase ambiguity(String code, String fieldPath, String question) {
        AmbiguityCase ambiguity = new AmbiguityCase();
        ambiguity.setCode(code); ambiguity.setFieldPath(fieldPath); ambiguity.setQuestion(question);
        ambiguity.setOptions(objectMapper.createArrayNode()); ambiguity.setStatus(AmbiguityStatus.OPEN);
        return ambiguity;
    }
}
