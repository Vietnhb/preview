package com.example.backend.ai.extraction.model;

import com.fasterxml.jackson.databind.JsonNode;

public record PhysicalRelation(
        String type,
        String subject,
        String object,
        JsonNode value,
        String unit,
        String sourceText) {
}
