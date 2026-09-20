package com.example.backend.physics.validation;

import com.example.backend.physics.model.ScalarField;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates solver transport output against the schema before persistence. */
public final class OutputContractValidator {
    private static final int MAX_SAMPLES = 1_000_001;

    private OutputContractValidator() {
    }

    public static void validate(String schemaId, JsonNode definition, SolverOutput output) {
        if (output == null) fail(schemaId, "output is null");
        List<Double> time = output.time();
        if (time == null || time.isEmpty() || time.size() > MAX_SAMPLES) fail(schemaId, "timeline is empty or exceeds limit");
        for (int i = 0; i < time.size(); i++) {
            Double value = time.get(i);
            if (value == null || !Double.isFinite(value) || value < 0) fail(schemaId, "timeline contains invalid value at " + i);
            if (i > 0 && value <= time.get(i - 1)) fail(schemaId, "timeline must be strictly increasing");
        }

        List<Map<String, List<Double>>> seriesGroups = List.of(output.positions(), output.velocities(),
                output.accelerations(), output.values());
        Set<String> produced = new LinkedHashSet<>();
        for (Map<String, List<Double>> group : seriesGroups) {
            if (group == null) continue;
            for (Map.Entry<String, List<Double>> entry : group.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) fail(schemaId, "blank output key");
                List<Double> values = entry.getValue();
                if (values == null || values.size() != time.size()) {
                    fail(schemaId, "output " + entry.getKey() + " has wrong length");
                }
                for (Double value : values) {
                    if (value == null || !Double.isFinite(value)) fail(schemaId, "output " + entry.getKey() + " is non-finite");
                }
                if (!produced.add(entry.getKey())) fail(schemaId, "duplicate output key " + entry.getKey());
            }
        }
        for (Map.Entry<String, ScalarField> entry : output.scalarFields().entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                fail(schemaId, "invalid scalar field key/value");
            }
            if (!produced.add(entry.getKey())) fail(schemaId, "duplicate scalar field key " + entry.getKey());
        }

        JsonNode probeSeries = definition == null ? null : definition.path("output").path("probeSeries");
        if (probeSeries != null && probeSeries.isArray()) {
            for (JsonNode key : probeSeries) {
                if (!key.isTextual() || key.asText().isBlank() || !produced.contains(key.asText())) {
                    fail(schemaId, "declared output is missing: " + key.asText());
                }
            }
        }
    }

    private static void fail(String schemaId, String detail) {
        throw new IllegalArgumentException("Output contract failed for schemaId=" + schemaId + ": " + detail);
    }
}
