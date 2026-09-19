package com.example.backend.extraction;

import com.example.backend.enums.ExtractionOutcome;
import com.example.backend.enums.ExtractionPath;
import com.fasterxml.jackson.databind.JsonNode;

public record ExtractionResult(
        SpecificationDocument document,
        ExtractionPath path,
        ExtractionOutcome outcome,
        String providerName,
        String modelVersion,
        JsonNode rawResponse,
        String errorMessage) {
}
