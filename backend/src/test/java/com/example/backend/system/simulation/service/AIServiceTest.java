package com.example.backend.system.simulation.service;

import com.example.backend.config.AIProperties;
import com.example.backend.config.JevProperties;
import com.example.backend.config.UploadProperties;
import com.example.backend.exception.ApiException;
import com.example.backend.integration.ai.AIClient;
import com.example.backend.system.physics.service.SchemaDefinitionService;
import com.example.backend.system.physics.service.SchemaEquationRuntime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AIServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = mock(HttpClient.class);

    private AIProperties settings(String visualProtocol, boolean structured) {
        return new AIProperties(
                new AIProperties.Provider("test-text-key", URI.create("https://text.example.test/v1"), "text-model", "vision-model",
                        "classpath:prompts/physics-ocr-system.txt", Duration.ofSeconds(5), Duration.ofSeconds(30), .2),
                new AIProperties.Visual("test-visual-key", visualProtocol, URI.create("https://visual.example.test/v1"), "visual-model",
                        .4, Duration.ofSeconds(60), true, structured, 32000, "low"));
    }

    private JevProperties routing() {
        return new JevProperties("test-routing-key", URI.create("https://router.example.test/v1"), "route-model",
                Duration.ofSeconds(20), .25, .08, 20000, 80000);
    }

    private AIClient client(String visualProtocol, boolean structured) {
        AIClient client = new AIClient(settings(visualProtocol, structured), routing(), json);
        ReflectionTestUtils.setField(client, "http", http);
        return client;
    }

    private AIService service(String visualProtocol, boolean structured) {
        return new AIService(mock(SchemaDefinitionService.class), mock(SchemaEquationRuntime.class),
                mock(GeneratedSimulationStorage.class), routing(), json, settings(visualProtocol, structured),
                client(visualProtocol, structured), new UploadProperties(1024, Set.of("image/png")), "test-plan-key", 50000);
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> response(int status, String body) {
        return mock(HttpResponse.class, invocation -> switch (invocation.getMethod().getName()) {
            case "statusCode" -> status;
            case "body" -> body;
            default -> RETURNS_DEFAULTS.answer(invocation);
        });
    }

    private String completion(String content) throws Exception {
        var response = json.createObjectNode();
        response.putArray("choices").addObject().putObject("message").put("content", content);
        return response.toString();
    }

    private JsonNode ask(AIClient client) {
        return client.visual(json.createObjectNode().put("description", "Motion"),
                "Visual instructions", json.createObjectNode().put("type", "object"));
    }

    @Test
    void compatibleProviderLabelsDoNotRequireAddingVendorNamesToBackendCode() throws Exception {
        var result = response(200, completion("{\"result\":\"ok\"}"));
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(result);
        assertEquals("ok", ask(client("groq", true)).path("result").asText());
        assertEquals("ok", ask(client("future_vendor", true)).path("result").asText());
    }

    @Test
    void openRouterUsesItsTokenAndReasoningFieldsWithStrictContract() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(200, completion("{\"visualProgram\":{}}")));
        assertTrue(ask(client("openrouter", true)).has("visualProgram"));
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requests.getValue();
        JsonNode body = json.readTree(body(request));
        assertEquals("https://visual.example.test/v1/chat/completions", request.uri().toString());
        assertEquals("Bearer test-visual-key", request.headers().firstValue("Authorization").orElseThrow());
        assertEquals(32000, body.path("max_tokens").asInt()); assertFalse(body.has("max_completion_tokens"));
        assertEquals("low", body.path("reasoning").path("effort").asText());
        assertTrue(body.path("provider").path("require_parameters").asBoolean());
        assertEquals("json_schema", body.path("response_format").path("type").asText());
        assertEquals("Visual instructions", body.path("messages").path(0).path("content").asText());
    }

    @Test
    void unsupportedStructuredParametersFallBackToPromptContract() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(400, "{\"error\":{\"message\":\"Unsupported response_format\"}}"),
                        response(200, completion("```json\n{\"visualProgram\":{}}\n```")));
        assertTrue(ask(client("openai_compatible", true)).has("visualProgram"));
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        JsonNode strict = json.readTree(body(requests.getAllValues().get(0)));
        JsonNode fallback = json.readTree(body(requests.getAllValues().get(1)));
        assertEquals(32000, strict.path("max_completion_tokens").asInt());
        assertEquals("low", strict.path("reasoning_effort").asText());
        assertFalse(fallback.has("response_format"));
        assertTrue(fallback.path("messages").path(0).path("content").asText().contains("RESPONSE CONTRACT"));
    }

    @Test
    void providerFailureDoesNotRetryUnrelatedErrorsOrLeakCredential() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(500, "{\"error\":{\"message\":\"test-visual-key denied\"}}"));
        ApiException error = assertThrows(ApiException.class, () -> ask(client("openai_compatible", true)));
        assertTrue(error.getMessage().contains("HTTP 500"));
        assertTrue(error.getMessage().contains("Vui lòng thử lại"));
        assertFalse(error.getMessage().contains("denied"));
        assertFalse(error.getMessage().contains("test-visual-key"));
        verify(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void geminiInteractionKeepsHeadersEndpointAndOutputShape() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response(200, "{\"steps\":[{\"type\":\"model_output\",\"content\":[{\"text\":\"{\\\"visualProgram\\\":{}}\"}]}]}"));
        assertTrue(ask(client("gemini_interactions", true)).has("visualProgram"));
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requests.getValue();
        JsonNode body = json.readTree(body(request));
        assertEquals("https://visual.example.test/v1", request.uri().toString());
        assertEquals("test-visual-key", request.headers().firstValue("x-goog-api-key").orElseThrow());
        assertEquals("2026-05-20", request.headers().firstValue("Api-Revision").orElseThrow());
        assertTrue(request.headers().firstValue("Authorization").isEmpty());
        assertFalse(body.path("store").asBoolean(true));
        assertEquals("application/json", body.path("response_format").path("mime_type").asText());
    }

    @Test
    void messagePartsAndSurroundingTextStillProduceSingleJsonObject() throws Exception {
        var parts = json.createArrayNode();
        parts.addObject().put("text", "Here is the result: ");
        parts.addObject().put("text", "{\"visualProgram\":{}}\n");
        JsonNode value = ReflectionTestUtils.invokeMethod(client("openai_compatible", false), "parseModelJson", parts);
        assertNotNull(value); assertTrue(value.has("visualProgram"));
        var error = assertThrows(java.lang.reflect.UndeclaredThrowableException.class,
                () -> ReflectionTestUtils.invokeMethod(client("openai_compatible", false), "parseModelJson", json.createObjectNode()));
        assertInstanceOf(java.io.IOException.class, error.getCause());
    }

    @Test
    void invalidImagesAndUnconfirmedTextStopBeforeAnyExternalRequest() {
        AIService service = service("openai_compatible", false);
        assertThrows(ApiException.class, () -> service.understandImage(new byte[0], "image/png", null));
        assertThrows(ApiException.class, () -> service.understandImage(new byte[1025], "image/png", null));
        assertThrows(ApiException.class, () -> service.understandImage(new byte[2], "text/plain", null));
        assertThrows(ApiException.class, () -> service.understandText(null, "ocr-session", "", null));
        verifyNoInteractions(http);
    }

    private static String body(HttpRequest request) {
        CompletableFuture<String> result = new CompletableFuture<>();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) { byte[] part = new byte[buffer.remaining()]; buffer.get(part); bytes.writeBytes(part); }
            public void onError(Throwable error) { result.completeExceptionally(error); }
            public void onComplete() { result.complete(bytes.toString(StandardCharsets.UTF_8)); }
        });
        return result.join();
    }
}
