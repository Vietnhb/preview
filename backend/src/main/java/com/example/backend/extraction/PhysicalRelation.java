package com.example.backend.extraction;

import com.fasterxml.jackson.databind.JsonNode;

public record PhysicalRelation(
        String type,
        String subject,
        String object,
        JsonNode value,
        String unit,
        String sourceText) {
}
