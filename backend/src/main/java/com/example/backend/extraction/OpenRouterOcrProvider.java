package com.example.backend.extraction;

import java.util.List;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.example.backend.entity.OcrStatus;
import com.fasterxml.jackson.databind.JsonNode;

@Component
public class OpenRouterOcrProvider implements OcrProvider {

    private static final String DEFAULT_MODEL = "openrouter/free";

    private final OpenRouterClient client;
    private final String model;

    public OpenRouterOcrProvider(OpenRouterClient client, Environment environment) {
        this.client = client;
        this.model = environment.getProperty("OPENROUTER_OCR_MODEL", DEFAULT_MODEL).trim();
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
                    client.textMessage("system", "Read the physics problem from the image. Return JSON: {\"text\":\"exact text\"}."),
                    client.imageMessage("Read all visible problem text exactly.", contentType, content)));
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
