package com.example.backend.ai.extraction.model;

import com.fasterxml.jackson.databind.JsonNode;

public record ProviderExtractionResult(
        SpecificationDocument document,
        JsonNode rawResponse) {
}
