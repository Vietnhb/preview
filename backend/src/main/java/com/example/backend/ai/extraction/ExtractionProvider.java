package com.example.backend.ai.extraction;

import java.util.Map;

import com.example.backend.ai.extraction.model.ProviderExtractionResult;
import com.example.backend.entity.enums.ExtractionPath;
import com.fasterxml.jackson.databind.JsonNode;

public interface ExtractionProvider {
    String providerName();

    String modelVersion();

    ExtractionPath path();

    boolean isAvailable();

    ProviderExtractionResult extract(String text);

    ProviderExtractionResult resolveAmbiguities(
            String originalText,
            JsonNode currentSpecification,
            Map<String, String> answers);
}
