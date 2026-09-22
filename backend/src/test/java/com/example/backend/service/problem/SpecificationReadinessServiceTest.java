package com.example.backend.service.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.backend.entity.enums.AmbiguityStatus;
import com.example.backend.entity.enums.ConfirmationState;
import com.example.backend.entity.problem.AmbiguityCase;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.entity.problem.Specification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class SpecificationReadinessServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void appendsSchemaUnitToAiQuestionWithoutQuantitySpecificTemplates() throws Exception {
        SchemaDefinitionService schemas = mock(SchemaDefinitionService.class);
        JsonNode definition = objectMapper.readTree("""
                {
                  "requiredQuantities": [
                    {"key":"velocity_2","allowedUnits":["m/s"]}
                  ]
                }
                """);
        SchemaVersion version = new SchemaVersion();
        version.setSchemaId("dynamic-example");
        version.setVersion("1.0");
        version.setDefinition(definition);

        Specification specification = new Specification();
        specification.setSchemaId("dynamic-example");
        specification.setSchemaVersion("1.0");
        specification.setConfidence(BigDecimal.ONE);
        specification.setConfirmationState(ConfirmationState.UNRESOLVED);
        specification.setObjects(objectMapper.createArrayNode());
        specification.setQuantities(objectMapper.createArrayNode());
        specification.setRelations(objectMapper.createArrayNode());

        AmbiguityCase ambiguity = new AmbiguityCase();
        ambiguity.setCode("missing.velocity_2");
        ambiguity.setFieldPath("quantities.velocity_2");
        ambiguity.setQuestion("Vận tốc ban đầu của vật 2 là bao nhiêu?");
        ambiguity.setOptions(objectMapper.createArrayNode());
        ambiguity.setStatus(AmbiguityStatus.OPEN);
        specification.addAmbiguityCase(ambiguity);

        when(schemas.requirePublishedVersion("dynamic-example", "1.0")).thenReturn(version);
        when(schemas.canonicalizeQuantities(any(), eq(definition)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(schemas.materializeDefaults(any(), eq(definition)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(schemas.missingRequiredQuantities(any(), eq(definition)))
                .thenReturn(List.of(new SchemaDefinitionService.RequiredGap("velocity_2", "m/s")));

        new SpecificationReadinessService(schemas, objectMapper).ensureRequiredAmbiguities(specification);

        assertThat(ambiguity.getQuestion()).isEqualTo("Vận tốc ban đầu của vật 2 là bao nhiêu? (m/s)");
        assertThat(ambiguity.getCode()).isEqualTo("schema.required.velocity_2");
    }
}
