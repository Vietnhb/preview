package com.example.backend.ai.client;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.example.backend.config.properties.AiProviderProperties;
import com.example.backend.service.school.SchoolService;
import com.example.backend.ai.extraction.validation.StrictJsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class ChatCompletionClient {

    private static final String CONTENT = "content";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final int maxCompletionTokens;
    private final AiProviderProperties properties;
    private final SchoolService schoolService;
    private final JsonNode specificationSchema;
    private final JsonNode ambiguityQuestionsSchema;
    private final JsonNode visualBindingsSchema;
    private final JsonNode assetRequestSummarySchema;

    public ChatCompletionClient(RestClient.Builder builder, ObjectMapper objectMapper, AiProviderProperties properties,
            SchoolService schoolService, ResourceLoader resourceLoader) {
        this.schoolService = schoolService;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.apiKey = properties.apiKey();
        this.maxCompletionTokens = properties.maxCompletionTokens();
        try (var input = resourceLoader.getResource(
                "classpath:prompts/physics-specification-response-schema.json").getInputStream()) {
            this.specificationSchema = objectMapper.readTree(input);
            try (var questionsInput = resourceLoader.getResource(
                    "classpath:prompts/ambiguity-questions-response-schema.json").getInputStream()) {
                this.ambiguityQuestionsSchema = objectMapper.readTree(questionsInput);
                try (var visualBindingsInput = resourceLoader.getResource(
                        "classpath:prompts/visual-bindings-response-schema.json").getInputStream()) {
                    this.visualBindingsSchema = objectMapper.readTree(visualBindingsInput);
                    try (var assetSummaryInput = resourceLoader.getResource(
                            "classpath:prompts/asset-request-summary-response-schema.json").getInputStream()) {
                        this.assetRequestSummarySchema = objectMapper.readTree(assetSummaryInput);
                    }
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load the AI structured-output schema", exception);
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = builder.requestFactory(requestFactory).baseUrl(properties.baseUrl().toString()).build();
    }

    public boolean isAvailable() {
        return StringUtils.hasText(apiKey);
    }

    public Completion completeStructured(String model, List<Map<String, Object>> messages) {
        return complete(model, messages, specificationResponseFormat(), true);
    }

    public Completion completeJson(String model, List<Map<String, Object>> messages) {
        return complete(model, messages, Map.of("type", "json_object"), false);
    }

    public Completion completeAmbiguityQuestions(String model, List<Map<String, Object>> messages) {
        return complete(model, messages, structuredResponseFormat(
                "physics_ambiguity_questions", ambiguityQuestionsSchema), false);
    }

    public Completion completeVisualBindings(String model, List<Map<String, Object>> messages) {
        return complete(model, messages, structuredResponseFormat(
                "physics_visual_bindings", visualBindingsSchema), false);
    }

    public Completion completeAssetRequestSummary(String model, List<Map<String, Object>> messages) {
        return complete(model, messages, structuredResponseFormat(
                "physics_asset_request_summary", assetRequestSummarySchema), false);
    }

    private Completion complete(String model, List<Map<String, Object>> messages,
            Map<String, Object> responseFormat, boolean reasoning) {
        if (!isAvailable()) {
            throw new IllegalStateException("AI provider is not configured.");
        }
        if (!StringUtils.hasText(model)) {
            throw new IllegalStateException("AI model is not configured.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", properties.temperature());
        body.put("max_completion_tokens", maxCompletionTokens);
        body.put("response_format", responseFormat);
        if (reasoning) {
            body.put("reasoning_effort", properties.reasoningEffort());
            body.put("include_reasoning", false);
        }

        JsonNode response = schoolService.meterAiCall(() -> {
            return restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(JsonNode.class);
        });

        if (response == null || response.path("choices").isEmpty()) {
            throw new IllegalStateException("AI provider returned an empty response.");
        }
        String content = response.path("choices").path(0).path("message").path(CONTENT).asText();
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("AI provider returned empty content.");
        }
        return new Completion(response.path("model").asText(model), content, response);
    }

    public Map<String, Object> textMessage(String role, String content) {
        return Map.of("role", role, CONTENT, content);
    }

    public Map<String, Object> imageMessage(String prompt, String contentType, byte[] content) {
        String dataUrl = "data:" + contentType + ";base64," + java.util.Base64.getEncoder().encodeToString(content);
        return Map.of(
                "role", "user",
                CONTENT, List.of(
                        Map.of("type", "text", "text", prompt),
                        Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))));
    }

    public JsonNode parseJson(String content) {
        try {
            return StrictJsonParser.parse(objectMapper, content);
        } catch (Exception exception) {
            throw new IllegalStateException("AI provider returned invalid JSON.", exception);
        }
    }

    private Map<String, Object> specificationResponseFormat() {
        return structuredResponseFormat("physics_specification", specificationSchema);
    }

    private Map<String, Object> structuredResponseFormat(String name, JsonNode schema) {
        return Map.of("type", "json_schema", "json_schema", Map.of(
                "name", name,
                "strict", properties.strictStructuredOutput(),
                "schema", schema));
    }

    public record Completion(String model, String content, JsonNode rawResponse) {
    }
}
