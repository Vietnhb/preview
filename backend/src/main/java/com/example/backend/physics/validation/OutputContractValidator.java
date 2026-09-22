package com.example.backend.physics.validation;

import com.example.backend.physics.compatibility.LegacySolverOutputAdapter;
import com.example.backend.exception.OutputContractException;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.output.PhysicsOutput;
import com.example.backend.physics.output.PhysicsOutputFrame;
import com.example.backend.physics.output.PhysicsOutputFrameMapper;
import com.example.backend.physics.output.PhysicsOutputValidator;
import com.example.backend.physics.output.PhysicsOutputContract;
import com.example.backend.service.problem.CompiledSchema;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Validates legacy solver transport output through the typed output boundary. */
public final class OutputContractValidator {

    private OutputContractValidator() { }

    public static final int MAX_SAMPLES = 1_000_001;

    /** Validates a compiled per-output contract when the schema version declares one. */
    public static void validate(CompiledSchema schema, JsonNode definition, SolverOutput output) {
        validate(schema, definition, output, Map.of());
    }

    /** Validates a grouped compatibility result with catalog source bindings. */
    public static void validate(CompiledSchema schema, JsonNode definition, SolverOutput output,
                                Map<String, List<OutputSourceBinding>> sourceBindings) {
        if (schema == null) throw new IllegalArgumentException("Compiled schema is required for output validation");
        PhysicsOutputContract contract = schema.outputContract(MAX_SAMPLES);
        if (contract == null) {
            validate(schema.schemaId(), definition, output);
            return;
        }
        if (output == null) fail(schema.schemaId(), "output is null");
        Map<String, String> units = new LinkedHashMap<>(declaredUnits(definition));
        schema.outputDefinitions().forEach((key, value) -> units.put(key, value.unit()));
        try {
            PhysicsOutputFrame frame = sourceBindings == null || sourceBindings.isEmpty()
                    ? LegacySolverOutputAdapter.adapt(output, units)
                    : PhysicsOutputFrameMapper.fromSolverOutput(output, contract, sourceBindings);
            PhysicsOutputValidator.validate(contract, frame);
        } catch (OutputContractException failure) {
            throw failure;
        } catch (IllegalArgumentException failure) {
            throw new OutputContractException("Output contract failed for schemaId=" + schema.schemaId()
                    + " schemaVersion=" + schema.version() + ": " + failure.getMessage(), failure);
        }
    }

    /** Validates a frame already emitted by the typed module boundary. */
    public static void validate(CompiledSchema schema, PhysicsOutputFrame frame) {
        if (schema == null) throw new IllegalArgumentException("Compiled schema is required for output validation");
        PhysicsOutputContract contract = schema.outputContract(MAX_SAMPLES);
        if (contract == null) {
            throw new OutputContractException("Output contract is missing for schemaId=" + schema.schemaId()
                    + " schemaVersion=" + schema.version());
        }
        try {
            PhysicsOutputValidator.validate(contract, frame);
        } catch (OutputContractException failure) {
            throw failure;
        } catch (IllegalArgumentException failure) {
            throw new OutputContractException("Output contract failed for schemaId=" + schema.schemaId()
                    + " schemaVersion=" + schema.version() + ": " + failure.getMessage(), failure);
        }
    }

    /**
     * Compatibility entry point until published catalog versions have typed
     * output declarations. New module paths should call PhysicsOutputValidator
     * with a compiled PhysicsOutputContract directly.
     */
    public static void validate(String schemaId, JsonNode definition, SolverOutput output) {
        if (output == null) fail(schemaId, "output is null");
        PhysicsOutputFrame frame;
        try {
            frame = LegacySolverOutputAdapter.adapt(output, declaredUnits(definition));
            PhysicsOutputValidator.validateStructure(schemaId, MAX_SAMPLES, frame);
        } catch (IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("Output contract failed")) {
                throw exception;
            }
            throw new OutputContractException("Output contract failed for schemaId=" + schemaId + ": "
                    + exception.getMessage(), exception);
        }

        Set<String> produced = frame.outputs().stream().map(PhysicsOutput::key).collect(Collectors.toSet());
        JsonNode probeSeries = definition == null ? null : definition.path("output").path("probeSeries");
        if (probeSeries != null && probeSeries.isArray()) {
            for (JsonNode key : probeSeries) {
                if (!key.isTextual() || key.asText().isBlank() || !produced.contains(key.asText())) {
                    fail(schemaId, "declared output is missing: " + key.asText());
                }
            }
        }
    }

    private static Map<String, String> declaredUnits(JsonNode definition) {
        if (definition == null || !definition.path("visualization").path("series").isArray()) return Map.of();
        Map<String, String> units = new LinkedHashMap<>();
        for (JsonNode series : definition.path("visualization").path("series")) {
            String source = series.path("source").asText("").trim();
            String unit = series.path("unit").asText("").trim();
            int lastSeparator = source.lastIndexOf('.');
            String key = lastSeparator >= 0 ? source.substring(lastSeparator + 1) : "";
            if (!key.isBlank() && !unit.isBlank()) units.putIfAbsent(key, unit);
        }
        return Map.copyOf(units);
    }

    private static void fail(String schemaId, String detail) {
        throw new OutputContractException("Output contract failed for schemaId=" + schemaId + ": " + detail);
    }
}
