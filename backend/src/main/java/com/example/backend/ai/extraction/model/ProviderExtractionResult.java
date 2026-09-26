package com.example.backend.ai.extraction.model;

import com.fasterxml.jackson.databind.JsonNode;

public record ProviderExtractionResult(
        SpecificationDocument document,
        JsonNode rawResponse,
        java.util.List<ResolutionDecision> resolutionDecisions) {
    public ProviderExtractionResult {
        resolutionDecisions = resolutionDecisions == null ? java.util.List.of() : java.util.List.copyOf(resolutionDecisions);
    }
}
