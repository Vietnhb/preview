package com.example.backend.ai.extraction.model;

import com.example.backend.entity.enums.ExtractionOutcome;
import com.example.backend.entity.enums.ExtractionPath;
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
