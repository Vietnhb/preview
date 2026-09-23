package com.example.backend.ai.extraction;

import java.util.Map;
import java.util.List;

import com.example.backend.ai.extraction.model.ProviderExtractionResult;
import com.example.backend.ai.extraction.model.AmbiguityItem;
import com.example.backend.ai.extraction.model.ConversationTurn;
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

    /**
     * Gives the model a bounded, machine-generated contract finding so it can
     * ask for clarification instead of silently repairing or dropping facts.
     */
    default ProviderExtractionResult extract(String text, SchemaRoutingDecision routingDecision,
            List<String> verificationFindings) {
        return extract(text, routingDecision);
    }

    default List<AmbiguityItem> phraseVerificationQuestions(String originalText, List<String> findings) {
        throw new UnsupportedOperationException("Verification question phrasing is not supported.");
    }

    default List<AmbiguityItem> phraseVerificationQuestions(String originalText, List<String> findings,
            List<ConversationTurn> conversation) {
        return phraseVerificationQuestions(originalText, findings);
    }

    ProviderExtractionResult resolveAmbiguities(
            String originalText,
            JsonNode currentSpecification,
            Map<String, String> answers);

    /**
     * Resolves an ambiguity with optional pinned visual-routing context. The
     * default keeps existing providers source-compatible; providers that can
     * use the context may enrich a document with validated visual bindings.
     */
    default ProviderExtractionResult resolveAmbiguities(
            String originalText,
            JsonNode currentSpecification,
            Map<String, String> answers,
            SchemaRoutingDecision visualRouting) {
        return resolveAmbiguities(originalText, currentSpecification, answers);
    }

    default ProviderExtractionResult resolveAmbiguities(
            String originalText,
            JsonNode currentSpecification,
            Map<String, String> answers,
            SchemaRoutingDecision visualRouting,
            List<ConversationTurn> conversation) {
        return resolveAmbiguities(originalText, currentSpecification, answers, visualRouting);
    }

    default ProviderExtractionResult bindVisualAssets(String originalText, JsonNode currentSpecification,
            SchemaRoutingDecision assetRoute, List<ConversationTurn> conversation) {
        return resolveAmbiguities(originalText, currentSpecification, Map.of(), assetRoute, conversation);
    }

    default JsonNode summarizeAssetRequests(String originalText, JsonNode confirmedSpecification) {
        throw new UnsupportedOperationException("Asset request summarization is not supported.");
    }
}
