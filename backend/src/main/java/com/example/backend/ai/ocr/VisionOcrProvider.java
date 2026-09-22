package com.example.backend.ai.ocr;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.backend.ai.client.ChatCompletionClient;
import com.example.backend.entity.enums.OcrStatus;
import com.example.backend.config.properties.AiProviderProperties;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class VisionOcrProvider implements OcrProvider {

    private final ChatCompletionClient client;
    private final String model;
    private final String providerName;
    private final String prompt;

    public VisionOcrProvider(ChatCompletionClient client, AiProviderProperties properties,
            ResourceLoader resourceLoader) {
        this.client = client;
        this.model = properties.visionModel();
        this.providerName = properties.name();
        try (var input = resourceLoader.getResource(properties.ocrPromptResource()).getInputStream()) {
            this.prompt = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot load the configured OCR prompt", exception);
        }
    }

    @Override
    public String providerName() {
        return providerName;
    }

    @Override
    public boolean isAvailable() {
        return client.isAvailable() && StringUtils.hasText(model);
    }

    @Override
    public OcrResult recognize(String contentType, byte[] content) {
        if (!isAvailable()) {
            return new OcrResult(OcrStatus.NOT_CONFIGURED, null, "Vision OCR is not configured.");
        }
        try {
            ChatCompletionClient.Completion completion = client.completeJson(model, List.of(
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
