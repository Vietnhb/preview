package com.example.backend.ai.extraction;

import java.util.List;
import java.util.Map;

import com.example.backend.ai.extraction.model.ConversationTurn;
import com.example.backend.ai.extraction.model.ProviderExtractionResult;
import com.example.backend.entity.enums.ExtractionPath;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;
import com.fasterxml.jackson.databind.JsonNode;

public interface ExtractionProvider {
    String providerName();

    String modelVersion();

    ExtractionPath path();

    boolean isAvailable();

    ProviderExtractionResult extract(String text, SchemaRoutingDecision routingDecision);

    ProviderExtractionResult resolveAmbiguities(
            String originalText,
            JsonNode currentSpecification,
            Map<String, String> answers);

    default ProviderExtractionResult resolveAmbiguities(
            String originalText,
            JsonNode currentSpecification,
            Map<String, String> answers,
            List<ConversationTurn> conversation) {
        return resolveAmbiguities(originalText, currentSpecification, answers);
    }

}
