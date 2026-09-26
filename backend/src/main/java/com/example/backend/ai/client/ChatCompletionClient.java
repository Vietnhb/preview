package com.example.backend.ai.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

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
    private static final String CHOICES = "choices";
    private static final String JSON_SCHEMA = "json_schema";
    private static final String PROPERTIES = "properties";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final int maxCompletionTokens;
    private final AiProviderProperties providerProperties;
    private final SchoolService schoolService;
    private final JsonNode specificationSchema;

    public ChatCompletionClient(RestClient.Builder builder, ObjectMapper objectMapper, AiProviderProperties properties,
            SchoolService schoolService, ResourceLoader resourceLoader) {
        this.schoolService = schoolService;
        this.objectMapper = objectMapper;
        this.providerProperties = properties;
        this.apiKey = properties.apiKey();
        this.maxCompletionTokens = properties.maxCompletionTokens();
        try (var input = resourceLoader.getResource(
                "classpath:prompts/physics-specification-response-schema.json").getInputStream()) {
            this.specificationSchema = objectMapper.readTree(input);
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

    public Completion completeStructured(String model, List<Map<String, Object>> messages,
            List<String> endConditionCapabilities) {
        return complete(model, messages, specificationResponseFormat(endConditionCapabilities), true);
    }

    public Completion completeJson(String model, List<Map<String, Object>> messages) {
        return complete(model, messages, Map.of("type", "json_object"), false);
    }

    public Completion completeJsonReasoned(String model, List<Map<String, Object>> messages) {
        return complete(model, messages, Map.of("type", "json_object"), true);
    }

    public Completion completeWithSchemaReasoned(String model, List<Map<String, Object>> messages,
            String name, JsonNode schema) {
        return complete(model, messages, structuredResponseFormat(name, schema), true);
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
        body.put("messages", jsonObjectMessages(messages, responseFormat));
        body.put("temperature", providerProperties.temperature());
        body.put("max_completion_tokens", maxCompletionTokens);
        body.put("response_format", responseFormat);
        if (reasoning) {
            body.put("reasoning_effort", providerProperties.reasoningEffort());
            body.put("include_reasoning", false);
        }

        JsonNode response = schoolService.meterAiCall(() -> restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(JsonNode.class));

        if (response == null || response.path(CHOICES).isEmpty()) {
            throw new IllegalStateException("AI provider returned an empty response.");
        }
        String content = response.path(CHOICES).path(0).path("message").path(CONTENT).asText();
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("AI provider returned empty content.");
        }
        String finish = response.path(CHOICES).path(0).path("finish_reason").asText();
        if (!"stop".equals(finish)) {
            throw new IllegalStateException("AI response was not completed: " + finish);
        }
        Object declared = responseFormat.get(JSON_SCHEMA);
        if (declared instanceof Map<?, ?> contract && contract.get("schema") instanceof JsonNode schema) {
            com.example.backend.ai.extraction.validation.ResponseSchemaValidator.validate(parseJson(content), schema);
        }
        return new Completion(response.path("model").asText(model), content, response);
    }

    /**
     * Groq's json_object response mode requires the word "json" in at least one
     * message. Keep that provider protocol detail at the client boundary so a
     * domain prompt cannot accidentally make an otherwise valid request fail.
     */
    private List<Map<String, Object>> jsonObjectMessages(List<Map<String, Object>> messages,
            Map<String, Object> responseFormat) {
        if (!"json_object".equals(responseFormat.get("type"))
                || messages.stream().map(String::valueOf)
                        .map(value -> value.toLowerCase(Locale.ROOT))
                        .anyMatch(value -> value.contains("json"))) {
            return messages;
        }
        var guarded = new ArrayList<Map<String, Object>>(messages.size() + 1);
        guarded.add(textMessage("system", "Return valid json only."));
        guarded.addAll(messages);
        return List.copyOf(guarded);
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

    private Map<String, Object> specificationResponseFormat(List<String> endConditionCapabilities) {
        JsonNode schema = specificationSchema.deepCopy();
        if (endConditionCapabilities != null && endConditionCapabilities.isEmpty()
                && schema.path(PROPERTIES) instanceof com.fasterxml.jackson.databind.node.ObjectNode schemaProperties) {
            schemaProperties.set("endCondition", objectMapper.createObjectNode().put("type", "null"));
        } else if (endConditionCapabilities != null
                && schema.path(PROPERTIES).path("endCondition").path(PROPERTIES).path("type")
                        instanceof com.fasterxml.jackson.databind.node.ObjectNode typeSchema) {
            var values = objectMapper.createArrayNode();
            endConditionCapabilities.stream().filter(StringUtils::hasText).distinct().forEach(values::add);
            typeSchema.set("enum", values);
        }
        return structuredResponseFormat("physics_specification", schema);
    }

    private Map<String, Object> structuredResponseFormat(String name, JsonNode schema) {
        return Map.of("type", JSON_SCHEMA, JSON_SCHEMA, Map.of(
                "name", name,
                "strict", providerProperties.strictStructuredOutput(),
                "schema", schema));
    }

    public record Completion(String model, String content, JsonNode rawResponse) {
    }
}
