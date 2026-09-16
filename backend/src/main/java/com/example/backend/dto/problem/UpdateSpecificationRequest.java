package com.example.backend.dto.problem;

import com.fasterxml.jackson.databind.JsonNode;

/** Teacher-reviewed structured specification fields. */
public record UpdateSpecificationRequest(
        JsonNode objects,
        JsonNode quantities,
        JsonNode relations,
        JsonNode endCondition) {
}
