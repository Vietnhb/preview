package com.example.backend.ai.extraction.model;

import com.example.backend.entity.enums.ExtractionOutcome;
import com.example.backend.entity.enums.ExtractionPath;
import com.fasterxml.jackson.databind.JsonNode;
import com.example.backend.schema.routing.model.SchemaRoutingDecision;

public record ExtractionResult(
        SpecificationDocument document,
        ExtractionPath path,
        ExtractionOutcome outcome,
        String providerName,
        String modelVersion,
        JsonNode rawResponse,
        String errorMessage,
        SchemaRoutingDecision routingDecision) {

    public ExtractionResult(SpecificationDocument document, ExtractionPath path, ExtractionOutcome outcome,
            String providerName, String modelVersion, JsonNode rawResponse, String errorMessage) {
        this(document, path, outcome, providerName, modelVersion, rawResponse, errorMessage, null);
    }
}
