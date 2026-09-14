package com.example.backend.extraction;

import com.fasterxml.jackson.databind.JsonNode;

public record ProviderExtractionResult(
        SpecificationDocument document,
        JsonNode rawResponse) {
}
