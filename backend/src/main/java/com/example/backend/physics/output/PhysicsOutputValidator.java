package com.example.backend.physics.output;

import com.example.backend.physics.model.ScalarField;
import com.example.backend.exception.OutputContractException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Validates typed outputs against a schema-pinned contract before persistence. */
public final class PhysicsOutputValidator {
    private PhysicsOutputValidator() { }

    public static void validate(PhysicsOutputContract contract, PhysicsOutputFrame frame) {
        if (contract == null || frame == null) throw new IllegalArgumentException("Output contract and frame are required");
        String prefix = "Output contract failed for schemaId=" + contract.schemaId()
                + " schemaVersion=" + contract.schemaVersion() + " modelId=" + contract.modelId() + ": ";
        List<Double> time = frame.timeSeconds();
        if (time.isEmpty() || time.size() > contract.maxSamples()) fail(prefix, "timeline is empty or exceeds limit");
        validateTime(prefix, time);
        if (frame.outputs().size() > contract.outputs().size()) fail(prefix, "undeclared outputs are present");

        Map<String, PhysicsOutput> produced = new HashMap<>();
        for (PhysicsOutput output : frame.outputs()) {
            if (output == null) fail(prefix, "null output");
            if (produced.putIfAbsent(output.key(), output) != null) fail(prefix, "duplicate output key " + output.key());
            PhysicsOutputContract.OutputDefinition definition = contract.outputs().get(output.key());
            if (definition == null) fail(prefix, "undeclared output key " + output.key());
            if (definition.kind() != output.kind()) fail(prefix, "output " + output.key() + " has kind " + output.kind()
                    + ", expected " + definition.kind());
            if (output.unit().isEmpty()) fail(prefix, "output " + output.key() + " has no unit");
            String unit = output.unit().orElseThrow(() -> new IllegalArgumentException(
                    "output " + output.key() + " has no unit"));
            if (!definition.unit().equals(unit)) {
                fail(prefix, "output " + output.key() + " has unit " + unit
                        + ", expected " + definition.unit());
            }
            validateShape(prefix, contract.maxSamples(), time, output);
        }
        for (Map.Entry<String, PhysicsOutputContract.OutputDefinition> expected : contract.outputs().entrySet()) {
            if (expected.getValue().required() && !produced.containsKey(expected.getKey())) {
                fail(prefix, "required output is missing: " + expected.getKey());
            }
        }
    }

    /** Structural validation used by the versioned adapter for older catalog entries. */
    public static void validateStructure(String schemaId, int maxSamples, PhysicsOutputFrame frame) {
        if (frame == null) fail(schemaId, "output frame is null");
        List<Double> time = frame.timeSeconds();
        if (time.isEmpty() || time.size() > maxSamples) fail(schemaId, "timeline is empty or exceeds limit");
        validateTime(schemaId, time);
        Map<String, PhysicsOutput> produced = new HashMap<>();
        for (PhysicsOutput output : frame.outputs()) {
            if (output == null) fail(schemaId, "null output");
            if (produced.putIfAbsent(output.key(), output) != null) fail(schemaId, "duplicate output key " + output.key());
            validateShape(schemaId, maxSamples, time, output);
        }
    }

    private static void validateShape(String prefix, int maxSamples, List<Double> frameTime, PhysicsOutput output) {
        switch (output) {
        case ScalarOutput scalar when !Double.isFinite(scalar.value()) ->
                fail(prefix, "scalar output " + scalar.key() + " is non-finite");
        case ScalarOutput scalar -> { /* Already finite. */ }
        case TimeSeriesOutput series when series.values().size() > maxSamples
                || !series.timeSeconds().equals(frameTime) ->
                fail(prefix, "time series " + series.key() + " has invalid axis or exceeds limit");
        case TimeSeriesOutput series -> { /* Axis already validated. */ }
        case VectorSeriesOutput vectors when vectors.values().size() > maxSamples
                || !vectors.timeSeconds().equals(frameTime) ->
                fail(prefix, "vector series " + vectors.key() + " has invalid axis or exceeds limit");
        case VectorSeriesOutput vectors -> { /* Axis already validated. */ }
        case ScalarFieldOutput(String key, ScalarField field) when field.time().size() > maxSamples
                || !field.time().equals(frameTime) ->
                fail(prefix, "scalar field " + key + " has invalid time axis or exceeds limit");
        case ScalarFieldOutput field -> { /* Axis already validated. */ }
        }
    }

    private static void validateTime(String schemaId, List<Double> time) {
        for (int index = 0; index < time.size(); index++) {
            Double value = time.get(index);
            if (value == null || !Double.isFinite(value) || value < 0) fail(schemaId, "timeline contains invalid value at " + index);
            if (index > 0 && value <= time.get(index - 1)) fail(schemaId, "timeline must be strictly increasing");
        }
    }

    private static void fail(String prefixOrSchema, String detail) {
        String prefix = prefixOrSchema.startsWith("Output contract failed")
                ? prefixOrSchema : "Output contract failed for schemaId=" + prefixOrSchema + ": ";
        throw new OutputContractException(prefix + detail);
    }
}
