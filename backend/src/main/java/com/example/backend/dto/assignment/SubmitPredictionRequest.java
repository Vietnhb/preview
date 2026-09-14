package com.example.backend.dto.assignment;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record SubmitPredictionRequest(@NotNull JsonNode predictions) {
}
