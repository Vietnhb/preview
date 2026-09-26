package com.example.backend.ai.simulation;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SimulationFlowResponse(
        UUID sessionId,
        String stage,
        String recognizedText,
        String displayText,
        String sourceMode,
        Double confidence,
        String message,
        String question,
        String explanation,
        List<Parameter> parameters,
        List<String> defaults,
        String schemaId,
        String code,
        JsonNode simulationSpec,
        Validation validation) {

    public record Parameter(String name, double value, String unit, double min, double max, String label) {
        public Parameter(String name, double value, String unit, double min, double max) {
            this(name, value, unit, min, max, name);
        }
    }
    public record Validation(String status, List<String> flags) {
        public Validation {
            flags = List.copyOf(flags == null ? List.of() : flags);
        }
    }
}
