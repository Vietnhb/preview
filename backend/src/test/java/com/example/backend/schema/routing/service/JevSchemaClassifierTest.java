package com.example.backend.schema.routing.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClient;

import com.example.backend.config.properties.JevProperties;
import com.example.backend.exception.EmbeddingUnavailableException;
import com.example.backend.simulation.assets.SvgAssetCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class JevSchemaClassifierTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient rest = mock(RestClient.class);
    private final RestClient.RequestBodySpec request = mock(RestClient.RequestBodySpec.class, RETURNS_SELF);
    private final RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
    private JevSchemaClassifier classifier;
    private ObjectNode answers;

    @BeforeEach
    void setUp() throws Exception {
        var builder = mock(RestClient.Builder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(rest);
        var uri = mock(RestClient.RequestBodyUriSpec.class);
        when(rest.post()).thenReturn(uri);
        when(uri.uri("/systemone")).thenReturn(request);
        when(request.retrieve()).thenReturn(response);
        var catalog = mock(SvgAssetCatalog.class);
        when(catalog.entries()).thenReturn(List.of(
                asset("red-car", "actor", "A red road car"),
                asset("blue-block", "actor", "A blue rectangular block"),
                asset("spring", "prop", "A metal coil spring")));
        when(catalog.checksum()).thenReturn("catalog-checksum");
        var properties = new JevProperties("test-key", URI.create("https://example.com/v1"), "jev-test",
                Duration.ofSeconds(5), 5, 0.25, 0.08, 20000, 30000);
        classifier = new JevSchemaClassifier(builder, properties, catalog);
        answers = (ObjectNode) mapper.readTree("""
                {
                  "schema":{"type":"choice","choice":"motion","confidence":0.98,
                    "probabilities":{"motion":0.99,"collision":0.01}},
                  "in_scope":{"type":"noul","noul":0.99},
                  "asset_red-car":{"type":"choice","choice":"EXACT","confidence":0.97,
                    "probabilities":{"EXACT":0.99,"SUBSTITUTE":0.01,"IRRELEVANT":0}},
                  "asset_blue-block":{"type":"choice","choice":"SUBSTITUTE","confidence":0.94,
                    "probabilities":{"EXACT":0.01,"SUBSTITUTE":0.97,"IRRELEVANT":0.02}},
                  "asset_spring":{"type":"choice","choice":"IRRELEVANT","confidence":0.98,
                    "probabilities":{"EXACT":0,"SUBSTITUTE":0.01,"IRRELEVANT":0.99}}
                }
                """);
        when(response.body(JsonNode.class)).thenAnswer(ignored -> mapper.createObjectNode()
                .put("model", "jev-test").set("answers", answers));
    }

    @Test
    void routesPhysicsAndEveryCatalogAssetInOneRequest() {
        var result = classify();

        verify(rest, times(1)).post();
        var captor = ArgumentCaptor.forClass(Object.class);
        verify(request).body(captor.capture());
        JsonNode body = mapper.valueToTree(captor.getValue());
        assertEquals("A red car moves along a road.", body.path("state").asText());
        assertEquals(5, body.path("questions").size());
        assertEquals("choice", body.at("/questions/asset_red-car/type").asText());
        assertEquals("A red road car", body.at("/questions/asset_red-car/instructions/asset/description").asText());
        assertEquals("motion", result.choice());
        assertEquals("catalog-checksum", result.assets().catalogChecksum());
        assertEquals(List.of("red-car", "blue-block"),
                result.assets().candidates().stream().map(item -> item.assetId()).toList());
        assertEquals(List.of("EXACT", "SUBSTITUTE"),
                result.assets().candidates().stream().map(item -> item.match()).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"schema", "in_scope", "asset_red-car", "asset_blue-block", "asset_spring"})
    void missingAnswerFailsInsteadOfInventingAnAsset(String missing) {
        answers.remove(missing);
        assertThrows(EmbeddingUnavailableException.class, this::classify);
    }

    @Test
    void refusesUndeclaredChoicesAndMalformedProbabilities() {
        ((ObjectNode) answers.path("asset_red-car")).put("choice", "new-svg");
        assertThrows(EmbeddingUnavailableException.class, this::classify);
        ((ObjectNode) answers.path("asset_red-car")).put("choice", "EXACT").put("confidence", "high");
        assertThrows(EmbeddingUnavailableException.class, this::classify);
        ((ObjectNode) answers.path("asset_red-car")).put("confidence", 0.9);
        ((ObjectNode) answers.at("/asset_red-car/probabilities")).remove("IRRELEVANT");
        assertThrows(EmbeddingUnavailableException.class, this::classify);
    }

    @Test
    void unrelatedCatalogProducesAnEmptyDecisionWithoutFallback() {
        for (String id : List.of("red-car", "blue-block", "spring")) {
            ((ObjectNode) answers.path("asset_" + id)).put("choice", "IRRELEVANT");
        }
        assertTrue(classify().assets().candidates().isEmpty());
    }

    private JevSchemaClassifier.Result classify() {
        return classifier.classify("A red car moves along a road.",
                Map.of("motion", "Uniform acceleration", "collision", "Interacting bodies"));
    }

    private SvgAssetCatalog.Asset asset(String id, String kind, String description) {
        return new SvgAssetCatalog.Asset(id, id, kind, id, description, List.of(), List.of(),
                id + ".svg", List.of(0d, 0d, 64d, 64d), "center", List.of());
    }
}
