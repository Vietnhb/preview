package com.example.backend.service.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.backend.ai.extraction.ExtractionProvider;
import com.example.backend.config.properties.AssetSelectionProperties;
import com.example.backend.entity.enums.ConfirmationState;
import com.example.backend.entity.problem.ProblemSubmission;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.entity.problem.Specification;
import com.example.backend.repository.problem.SpecificationRepository;
import com.example.backend.schema.routing.service.JevSchemaRoutingService;
import com.example.backend.service.account.CurrentUserService;
import com.example.backend.simulation.assets.AssetSelectionService;
import com.example.backend.simulation.assets.SvgAssetCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class AssetlessSimulationPreparationTest {
    @Test
    void graphOnlySchemaSkipsAssetSearchAndRemainsRunnable() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode entry;
        try (InputStream input = getClass().getResourceAsStream("/schemas/catalog.json")) {
            entry = java.util.stream.StreamSupport.stream(mapper.readTree(input).spliterator(), false)
                    .filter(item -> "ohms_law".equals(item.path("schemaId").asText())
                            && "1.1".equals(item.path("version").asText()))
                    .findFirst().orElseThrow();
        }
        SchemaVersion schema = new SchemaVersion();
        schema.setSchemaId(entry.path("schemaId").asText());
        schema.setVersion(entry.path("version").asText());
        schema.setDefinition(entry.path("definition"));
        assertFalse(schema.hasSvgAsset());

        SchemaDefinitionService schemas = mock(SchemaDefinitionService.class);
        JsonNode visualization = schema.getDefinition().path("visualization").deepCopy();
        when(schemas.requireCurrentApproved(schema.getSchemaId(), schema.getVersion())).thenReturn(schema);
        when(schemas.visualization(schema.getDefinition())).thenReturn(visualization);
        SpecificationReadinessService readiness = mock(SpecificationReadinessService.class);
        ExtractionProvider ai = mock(ExtractionProvider.class);
        JevSchemaRoutingService jev = mock(JevSchemaRoutingService.class);
        AssetSelectionService assets = new AssetSelectionService(mock(SvgAssetCatalog.class), schemas,
                new AssetSelectionProperties(0.8), mapper, mock(SpecificationRepository.class),
                mock(CurrentUserService.class), mock(ProblemResponseMapper.class));
        var applier = new AmbiguityResolutionApplier(mapper, ai, schemas, readiness, jev, assets);

        Specification specification = new Specification();
        specification.setSchemaId(schema.getSchemaId());
        specification.setSchemaVersion(schema.getVersion());
        specification.setContractVersion("1.0");
        specification.setTopic(entry.path("topic").asText());
        specification.setConfidence(BigDecimal.ONE);
        specification.setObjects(mapper.readTree("[{\"id\":\"object-1\",\"label\":\"Vật\",\"type\":\"body\",\"quantities\":[]}]"));
        specification.setQuantities(mapper.createArrayNode());
        specification.setRelations(mapper.createArrayNode());
        specification.setEndCondition(mapper.createObjectNode().put("type", "time_limit").put("duration", 8));
        specification.setConfirmationState(ConfirmationState.CONFIRMED);
        ProblemSubmission submission = new ProblemSubmission();
        submission.setEditableText("Một bài định luật Ohm");
        specification.setSubmission(submission);

        applier.prepareAssetsAfterConfirmation(specification, List.of());

        assertEquals("READY", specification.getAssetSelection().path("status").asText());
        assertEquals(0, specification.getAssetSelection().path("choices").size());
        assertEquals(visualization, AssetSelectionService.requireReady(specification));
        verifyNoInteractions(ai, jev);
    }
}
