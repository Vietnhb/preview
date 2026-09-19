package com.example.backend.ai.client;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.example.backend.config.properties.OpenRouterProperties;
import com.example.backend.service.school.SchoolService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class OpenRouterClient {

    private static final String CONTENT = "content";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final int maxTokens;
    private final OpenRouterProperties properties;
    private final SchoolService schoolService;

    public OpenRouterClient(RestClient.Builder builder, ObjectMapper objectMapper, OpenRouterProperties properties,
            SchoolService schoolService) {
        this.schoolService = schoolService;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.apiKey = properties.apiKey();
        this.maxTokens = properties.maxTokens();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = builder.requestFactory(requestFactory).baseUrl(properties.baseUrl().toString()).build();
    }

    public boolean isAvailable() {
        return StringUtils.hasText(apiKey);
    }

    public Completion complete(String model, List<Map<String, Object>> messages) {
        if (!isAvailable()) {
            throw new IllegalStateException("OpenRouter is not configured.");
        }
        if (!StringUtils.hasText(model)) {
            throw new IllegalStateException("OpenRouter model is not configured.");
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", messages,
                "temperature", 0,
                "max_tokens", maxTokens,
                "response_format", Map.of("type", "json_object"));

        JsonNode response = schoolService.meterAiCall(() -> {
            RestClient.RequestBodySpec request = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(properties.appUrl())) {
                request.header("HTTP-Referer", properties.appUrl());
            }
            if (StringUtils.hasText(properties.appName())) {
                request.header("X-OpenRouter-Title", properties.appName());
            }
            return request.body(body).retrieve().body(JsonNode.class);
        });

        if (response == null || response.path("choices").isEmpty()) {
            throw new IllegalStateException("OpenRouter returned an empty response.");
        }
        String content = response.path("choices").path(0).path("message").path(CONTENT).asText();
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("OpenRouter returned empty content.");
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
        String normalized = content.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        try {
            return objectMapper.readTree(normalized);
        } catch (Exception exception) {
            throw new IllegalStateException("OpenRouter returned invalid JSON.", exception);
        }
    }

    public record Completion(String model, String content, JsonNode rawResponse) {
    }
}
