package com.example.backend.ai.extraction.model;

import com.fasterxml.jackson.databind.JsonNode;

public record ProviderExtractionResult(
        SpecificationDocument document,
        JsonNode rawResponse,
        JsonNode assetSelection,
        java.util.List<ResolutionDecision> resolutionDecisions) {
    public ProviderExtractionResult {
        resolutionDecisions = resolutionDecisions == null ? java.util.List.of() : java.util.List.copyOf(resolutionDecisions);
    }

    public ProviderExtractionResult(SpecificationDocument document, JsonNode rawResponse) {
        this(document, rawResponse, null, java.util.List.of());
    }

    public ProviderExtractionResult(SpecificationDocument document, JsonNode rawResponse, JsonNode assetSelection) {
        this(document, rawResponse, assetSelection, java.util.List.of());
    }
}
