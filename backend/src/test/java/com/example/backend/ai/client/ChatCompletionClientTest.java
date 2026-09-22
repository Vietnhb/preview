package com.example.backend.ai.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.web.client.RestClient;

import com.example.backend.config.properties.AiProviderProperties;
import com.example.backend.service.school.SchoolService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ChatCompletionClientTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void extractionAlwaysSendsTheSchemaAndFlagOnlyControlsStrictness(boolean strict) throws Exception {
        var mapper = new ObjectMapper();
        var builder = mock(RestClient.Builder.class, RETURNS_SELF);
        var rest = mock(RestClient.class);
        when(builder.build()).thenReturn(rest);
        var uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        var request = mock(RestClient.RequestBodySpec.class, RETURNS_SELF);
        var response = mock(RestClient.ResponseSpec.class);
        when(rest.post()).thenReturn(uriSpec);
        when(uriSpec.uri("/chat/completions")).thenReturn(request);
        when(request.retrieve()).thenReturn(response);
        when(response.body(JsonNode.class)).thenReturn(mapper.readTree("""
                {"model":"test-model","choices":[{"message":{"content":"{}"}}]}
                """));
        var school = mock(SchoolService.class);
        when(school.meterAiCall(any())).thenAnswer(call ->
                call.<Supplier<JsonNode>>getArgument(0).get());
        var properties = new AiProviderProperties("test", "test-key", URI.create("https://example.com/v1"),
                "test-model", "test-model", "medium", 0, 1, strict,
                Duration.ofSeconds(1), Duration.ofSeconds(1), 1000,
                "classpath:prompts/physics-specification-system.txt", "classpath:prompts/physics-ocr-system.txt");
        var client = new ChatCompletionClient(builder, mapper, properties, school, new DefaultResourceLoader());

        client.completeStructured("test-model", List.of(Map.of("role", "user", "content", "Extract facts")));

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(request).body(captor.capture());
        var format = mapper.valueToTree(captor.getValue()).path("response_format");
        assertEquals("json_schema", format.path("type").asText());
        assertEquals(strict, format.path("json_schema").path("strict").asBoolean());
        try (var stream = getClass().getResourceAsStream("/prompts/physics-specification-response-schema.json")) {
            assertEquals(mapper.readTree(stream), format.path("json_schema").path("schema"));
        }
    }
}
