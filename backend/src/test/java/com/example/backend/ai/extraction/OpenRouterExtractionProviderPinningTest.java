package com.example.backend.ai.extraction;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import com.example.backend.ai.client.OpenRouterClient;
import com.example.backend.ai.normalization.UnitNormalizer;
import com.example.backend.config.properties.OpenRouterProperties;
import com.example.backend.config.properties.JevProperties;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.schema.routing.model.SchemaSelectionScore;
import com.example.backend.schema.routing.model.SchemaCandidate;
import com.example.backend.schema.routing.model.SchemaCandidate.VerificationEvidence;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class OpenRouterExtractionProviderPinningTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void staleCandidateIsRejectedBeforeThePromptIsSent() throws Exception {
        OpenRouterClient client = mock(OpenRouterClient.class);
        SchemaDefinitionService schemas = mock(SchemaDefinitionService.class);
        when(schemas.requireCurrentApproved("test_schema", "1.0"))
                .thenThrow(new IllegalStateException("candidate retired"));
        OpenRouterExtractionProvider provider = provider(client, schemas);

        assertThrows(IllegalStateException.class, () -> provider.extract("A sample problem", decision()));

        verify(client, never()).complete(anyString(), anyList());
    }

    @Test
    void schemaRetiredWhileAiRespondsIsRejectedAfterTheResponse() throws Exception {
        OpenRouterClient client = mock(OpenRouterClient.class);
        SchemaDefinitionService schemas = mock(SchemaDefinitionService.class);
        when(schemas.requireCurrentApproved("test_schema", "1.0"))
                .thenReturn(pinnedSchema()).thenThrow(new IllegalStateException("candidate retired"));
        when(client.textMessage(anyString(), anyString())).thenAnswer(invocation ->
                Map.of("role", invocation.getArgument(0), "content", invocation.getArgument(1)));
        JsonNode response = mapper.readTree("""
                {"contractVersion":"1.0","schemaVersion":"1.0","topic":"DYNAMICS","schemaId":"test_schema",
                 "objects":[],"quantities":[],"relations":[],"endCondition":{"type":"time_limit","duration":1},
                 "confidence":1,"ambiguities":[]}
                """);
        when(client.complete(eq("test-model"), anyList())).thenReturn(
                new OpenRouterClient.Completion("test-model", "{}", mapper.createObjectNode()));
        when(client.parseJson(anyString())).thenReturn(response);
        OpenRouterExtractionProvider provider = provider(client, schemas);

        assertThrows(IllegalStateException.class, () -> provider.extract("A sample problem", decision()));

        verify(schemas, times(3)).requireCurrentApproved("test_schema", "1.0");
        verify(client, times(2)).complete(eq("test-model"), anyList());
    }

    private OpenRouterExtractionProvider provider(OpenRouterClient client, SchemaDefinitionService schemas) {
        var ai = new OpenRouterProperties("test-key", URI.create("https://openrouter.ai/api/v1"), "test-model",
                "ocr-model", Duration.ofSeconds(1), Duration.ofSeconds(1), 1_000,
                "classpath:prompts/physics-specification-system.txt",
                "classpath:prompts/physics-specification-system.txt", "", "");
        var routing = new JevProperties("test-key", URI.create("https://api.typesafe.ai/v1"), "jev-latest",
                Duration.ofSeconds(1), 2, 0.25, 0.08, 20_000, 30_000);
        return new OpenRouterExtractionProvider(client, mapper, new UnitNormalizer(mapper), schemas,
                ai, routing, new DefaultResourceLoader(), new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    private SchemaRoutingDecision decision() throws Exception {
        var contract = com.example.backend.ai.extraction.prompt.CandidateContractProjection.from(
                "test_schema", "1.0", "DYNAMICS", "Test schema", mapper.readTree("""
                        {"model":"test_schema","requiredQuantities":[{"key":"mass","aliases":[],"allowedUnits":["kg"]}],"optionalQuantities":[]}
                        """));
        var candidate = new SchemaCandidate(contract, new SchemaSelectionScore(1, 1),
                new VerificationEvidence(1, 1, 1, List.of("fixture")), 1);
        return new SchemaRoutingDecision(SchemaRoutingDecision.Status.SELECTED,
                "BACKEND_SELECTED", List.of(candidate), 1, 1);
    }

    private SchemaVersion pinnedSchema() throws Exception {
        SchemaVersion schema = new SchemaVersion();
        schema.setSchemaId("test_schema");
        schema.setVersion("1.0");
        schema.setTopic("DYNAMICS");
        schema.setName("Test schema");
        schema.setDefinition(mapper.readTree("""
                {"model":"test_schema","requiredQuantities":[{"key":"mass","aliases":[],"allowedUnits":["kg"]}],"optionalQuantities":[]}
                """));
        return schema;
    }
}
