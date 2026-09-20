package com.example.backend.ai.extraction;

import java.util.Map;

import com.example.backend.ai.extraction.model.ProviderExtractionResult;
import com.example.backend.entity.enums.ExtractionPath;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;
import com.fasterxml.jackson.databind.JsonNode;

public interface ExtractionProvider {
    String providerName();

    String modelVersion();

    ExtractionPath path();

    boolean isAvailable();

    ProviderExtractionResult extract(String text);

    default ProviderExtractionResult extract(String text, SchemaRoutingDecision routingDecision) {
        if (routingDecision == null) throw new IllegalArgumentException("Schema routing decision is required");
        return extract(text);
    }

    ProviderExtractionResult resolveAmbiguities(
            String originalText,
            JsonNode currentSpecification,
            Map<String, String> answers);
}
