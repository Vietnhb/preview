package com.example.backend.extraction;

import com.example.backend.entity.ExtractionOutcome;
import com.example.backend.entity.ExtractionPath;
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
