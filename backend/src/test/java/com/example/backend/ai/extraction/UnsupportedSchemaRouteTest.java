package com.example.backend.ai.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.backend.exception.ApiException;
import com.example.backend.schema.routing.model.SchemaCandidate;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;
import com.example.backend.schema.routing.service.JevSchemaRoutingService;
import com.example.backend.ai.extraction.model.ProviderExtractionResult;
import com.example.backend.ai.extraction.model.SpecificationDocument;
import com.fasterxml.jackson.databind.ObjectMapper;

class UnsupportedSchemaRouteTest {
    @Test
    void lowConfidenceRouteApologizesAndDoesNotCallExtractionModel() {
        ExtractionProvider provider = mock(ExtractionProvider.class);
        JevSchemaRoutingService routing = mock(JevSchemaRoutingService.class);
        when(provider.isAvailable()).thenReturn(true);
        var candidate = mock(SchemaCandidate.class);
        var decision = new SchemaRoutingDecision(SchemaRoutingDecision.Status.AMBIGUOUS,
                "JEV_LOW_CONFIDENCE", List.of(candidate), 0.21, 0.03);
        when(routing.route("unmatched request")).thenReturn(decision);
        var coordinator = new ExtractionCoordinator(provider, routing);

        ApiException failure = assertThrows(ApiException.class,
                () -> coordinator.extract("unmatched request"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, failure.getStatus());
        assertEquals("Xin lỗi, PhysLive chưa hỗ trợ mô tả này vì chưa xác định được schema vật lý phù hợp.",
                failure.getMessage());
        verify(provider, never()).extract("unmatched request", decision);
    }

    @Test
    void capacityMismatchIsNotMisreportedAsUnsupportedSchema() {
        var decision = new SchemaRoutingDecision(SchemaRoutingDecision.Status.AMBIGUOUS,
                "JEV_CAPACITY_EXCEEDED", List.of(mock(SchemaCandidate.class)), 0.9, 0.8);

        assertDoesNotThrow(() -> ExtractionCoordinator.requireSupportedSchema(decision));
    }

    @Test
    void extractionDoesNotClassifyOrBindAssetsBeforeTeacherConfirmsTheSpec() throws Exception {
        String text = "A body moves with initial velocity 10 m/s.";
        ExtractionProvider provider = mock(ExtractionProvider.class);
        JevSchemaRoutingService routing = mock(JevSchemaRoutingService.class);
        when(provider.isAvailable()).thenReturn(true);
        var candidate = mock(SchemaCandidate.class);
        var decision = new SchemaRoutingDecision(SchemaRoutingDecision.Status.SELECTED,
                "JEV_SCHEMA_SELECTED", List.of(candidate), 0.95, 0.8);
        when(routing.route(text)).thenReturn(decision);
        var document = new SpecificationDocument("1.0", "KINEMATICS", "kinematics", List.of(), List.of(),
                List.of(), new ObjectMapper().readTree("{\"type\":\"time_limit\",\"duration\":8}"),
                BigDecimal.ONE, List.of(), SpecificationDocument.CURRENT_SCHEMA_VERSION);
        when(provider.extract(text, decision)).thenReturn(new ProviderExtractionResult(document, null));
        when(routing.verify(text, decision, document)).thenReturn(new JevSchemaRoutingService.Verification(List.of()));

        var result = new ExtractionCoordinator(provider, routing).extract(text);

        assertEquals(null, result.assetSelection());
        verify(routing, never()).routeAssets(anyString(), any(SpecificationDocument.class), anyList(), any());
        verify(provider, never()).bindVisualAssets(anyString(), any(), any(), anyList());
    }

    @Test
    void capacityMismatchKeepsBothObjectsAndAsksBeforeAssetClassification() throws Exception {
        String text = "Two bodies move in opposite directions.";
        ExtractionProvider provider = mock(ExtractionProvider.class);
        JevSchemaRoutingService routing = mock(JevSchemaRoutingService.class);
        when(provider.isAvailable()).thenReturn(true);
        var decision = new SchemaRoutingDecision(SchemaRoutingDecision.Status.AMBIGUOUS,
                "JEV_CAPACITY_EXCEEDED", List.of(mock(SchemaCandidate.class)), 0.93, 0.8);
        when(routing.route(text)).thenReturn(decision);
        List<String> findings = List.of("issue=JEV_CAPACITY_EXCEEDED; knownObjects=2; actorCapacity=1");
        when(routing.capacityFindings(decision)).thenReturn(findings);
        var question = new com.example.backend.ai.extraction.model.AmbiguityItem(
                "jev.capacity.actor-count", "schemaId",
                "The chosen scene represents one body; keep both objects or approve a simplification?", List.of());
        var document = new SpecificationDocument("1.0", "DYNAMICS", "two_body", List.of(
                new com.example.backend.ai.extraction.model.PhysicalObject("body-1", "Body 1", "body", List.of()),
                new com.example.backend.ai.extraction.model.PhysicalObject("body-2", "Body 2", "body", List.of())),
                List.of(), List.of(), new ObjectMapper().readTree("{\"type\":\"time_limit\",\"duration\":8}"),
                BigDecimal.ONE, List.of(question), SpecificationDocument.CURRENT_SCHEMA_VERSION);
        when(provider.extract(text, decision, findings)).thenReturn(new ProviderExtractionResult(document, null));
        when(routing.verify(text, decision, document)).thenReturn(new JevSchemaRoutingService.Verification(List.of()));

        var result = new ExtractionCoordinator(provider, routing).extract(text);

        assertEquals(2, result.document().objects().size(), "Both physical objects must remain separate.");
        assertEquals("schemaId", result.document().ambiguities().getFirst().fieldPath());
        assertEquals(null, result.assetSelection());
        verify(provider).extract(text, decision, findings);
        verify(routing, never()).routeAssets(anyString(), any(SpecificationDocument.class), anyList(), any());
        verify(provider, never()).bindVisualAssets(anyString(), any(), any(), anyList());
    }
}
