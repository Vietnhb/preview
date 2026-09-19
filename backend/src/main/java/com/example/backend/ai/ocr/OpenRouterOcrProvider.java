package com.example.backend.ai.ocr;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.backend.ai.client.OpenRouterClient;
import com.example.backend.entity.enums.OcrStatus;
import com.example.backend.config.properties.OpenRouterProperties;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class OpenRouterOcrProvider implements OcrProvider {

    private final OpenRouterClient client;
    private final String model;
    private final String prompt;

    public OpenRouterOcrProvider(OpenRouterClient client, OpenRouterProperties properties,
            ResourceLoader resourceLoader) {
        this.client = client;
        this.model = properties.ocrModel();
        try (var input = resourceLoader.getResource(properties.ocrPromptResource()).getInputStream()) {
            this.prompt = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load the configured OCR prompt", exception);
        }
    }

    @Override
    public String providerName() {
        return "openrouter";
    }

    @Override
    public boolean isAvailable() {
        return client.isAvailable() && StringUtils.hasText(model);
    }

    @Override
    public OcrResult recognize(String contentType, byte[] content) {
        if (!isAvailable()) {
            return new OcrResult(OcrStatus.NOT_CONFIGURED, null, "OpenRouter OCR is not configured.");
        }
        try {
            OpenRouterClient.Completion completion = client.complete(model, List.of(
                    client.imageMessage(prompt, contentType, content)));
            JsonNode json = client.parseJson(completion.content());
            String text = json.path("text").asText();
            if (!StringUtils.hasText(text)) {
                return new OcrResult(OcrStatus.FAILED, null, "OCR returned empty text.");
            }
            return new OcrResult(OcrStatus.SUCCEEDED, text.trim(), null);
        } catch (RuntimeException exception) {
            return new OcrResult(OcrStatus.FAILED, null, "OCR provider failed.");
        }
    }
}
