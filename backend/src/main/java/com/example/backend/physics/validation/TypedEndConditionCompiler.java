package com.example.backend.physics.validation;

import com.example.backend.service.problem.CompiledSchema;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Compiles one request end condition against the already pinned schema output contract. */
public final class TypedEndConditionCompiler {
    private static final String QUANTITY = "quantity";
    private static final String VALUE = "value";
    private TypedEndConditionCompiler() { }

    public static EndConditionContract compile(CompiledSchema schema, JsonNode specification,
            double fallbackDuration) {
        Objects.requireNonNull(schema, "Pinned compiled schema is required");
        JsonNode condition = EndConditionResolver.normalize(specification, fallbackDuration);
        List<String> errors = EndConditionResolver.validateNode(condition);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Invalid end condition for schemaId=" + schema.schemaId()
                    + " schemaVersion=" + schema.version() + ": " + String.join("; ", errors));
        }
        EndConditionType type = EndConditionType.fromWireName(condition.path("type").asText(""));
        if (type == null) {
            throw new IllegalArgumentException("Unsupported end condition for schemaId=" + schema.schemaId());
        }
        return switch (type) {
            case TIME_LIMIT -> new EndConditionContract.TimeLimit(condition.path("duration").asDouble());
            case THRESHOLD -> new EndConditionContract.Threshold(
                    source(schema, condition.path(QUANTITY).asText("")),
                    ComparisonOperator.parse(condition.path("operator").asText()),
                    condition.path(VALUE).asDouble(), optionalMaxTime(condition));
            case CYCLE_COUNT -> new EndConditionContract.CycleCount(
                    source(schema, condition.path(QUANTITY).asText("")),
                    condition.path("count").asInt(), optionalMaxTime(condition));
            case EVENT -> compileEvent(schema, condition);
            case MANUAL -> new EndConditionContract.Manual(optionalMaxTime(condition));
        };
    }

    private static EndConditionContract.Event compileEvent(CompiledSchema schema, JsonNode condition) {
        JsonNode event = condition.path("event");
        EndConditionContract.EventKind kind = switch (event.path("type").asText("")
                .trim().toLowerCase(Locale.ROOT)) {
            case "contact" -> EndConditionContract.EventKind.CONTACT;
            case "collision" -> EndConditionContract.EventKind.COLLISION;
            default -> throw new IllegalArgumentException("Unsupported event type for schemaId=" + schema.schemaId());
        };
        List<String> entities = new ArrayList<>();
        event.path("entities").forEach(entity -> {
            if (entity.isTextual()) entities.add(entity.asText());
        });
                    String quantity = nonBlankText(event.get(QUANTITY));
        String firstQuantity = nonBlankText(event.get("firstQuantity"));
        String secondQuantity = nonBlankText(event.get("secondQuantity"));
        String markerQuantity = nonBlankText(event.get("markerQuantity"));
        if (kind == EndConditionContract.EventKind.COLLISION && firstQuantity == null
                && secondQuantity == null && markerQuantity == null && entities.size() >= 2) {
            // Legacy entity matching is allowed in the typed path only when the
            // pinned schema declares those position series as output sources.
            firstQuantity = "positions." + entities.get(0);
            secondQuantity = "positions." + entities.get(1);
        }
        return new EndConditionContract.Event(kind, entities,
                quantity == null ? null : source(schema, quantity),
                ComparisonOperator.parse(nonBlankText(event.get("operator"))),
                event.path(VALUE).isNumber() ? event.path(VALUE).asDouble() : null,
                firstQuantity == null ? null : source(schema, firstQuantity),
                secondQuantity == null ? null : source(schema, secondQuantity),
                markerQuantity == null ? null : source(schema, markerQuantity),
                optionalMaxTime(condition));
    }

    private static OutputSourceBinding source(CompiledSchema schema, String rawSource) {
        if (rawSource == null || rawSource.isBlank()) {
            throw new IllegalArgumentException("End-condition output source is required");
        }
        String raw = rawSource.trim();
        int separator = raw.lastIndexOf('.');
        OutputSourceBinding.Group requestedGroup = null;
        String key = raw;
        if (separator > 0) {
            requestedGroup = group(raw.substring(0, separator));
            if (requestedGroup == null) {
                throw new IllegalArgumentException("Unsupported end-condition output group for schemaId="
                        + schema.schemaId() + ": " + raw.substring(0, separator));
            }
            key = raw.substring(separator + 1).trim();
        }
        List<OutputSourceBinding> declared = schema.endConditionSources().getOrDefault(key, List.of());
        List<OutputSourceBinding> matches;
        if (requestedGroup == null) {
            matches = declared;
        } else {
            OutputSourceBinding.Group group = requestedGroup;
            matches = declared.stream().filter(binding -> binding.group() == group).toList();
            if (matches.isEmpty()) {
                // Visualization bindings are indexed by their typed output key,
                // while an end condition may name the grouped source key (for
                // example velocities.x). Resolve that relation from compiled
                // metadata instead of inferring it from a field-name prefix.
                String sourceKey = key;
                matches = schema.endConditionSources().values().stream()
                        .flatMap(List::stream)
                        .filter(binding -> binding.group() == group && binding.key().equals(sourceKey))
                        .distinct()
                        .toList();
            }
        }
        if (matches.size() != 1) {
            String reason = matches.isEmpty() ? "is not a declared time-series output"
                    : "is ambiguous; qualify it with its output group";
            throw new IllegalArgumentException("End-condition source schemaId=" + schema.schemaId()
                    + " schemaVersion=" + schema.version() + " " + reason + ": " + raw);
        }
        return matches.getFirst();
    }

    private static OutputSourceBinding.Group group(String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "value", "values" -> OutputSourceBinding.Group.VALUES;
            case "position", "positions" -> OutputSourceBinding.Group.POSITIONS;
            case "velocity", "velocities" -> OutputSourceBinding.Group.VELOCITIES;
            case "acceleration", "accelerations" -> OutputSourceBinding.Group.ACCELERATIONS;
            default -> null;
        };
    }

    private static Double optionalMaxTime(JsonNode condition) {
        JsonNode maxTime = condition.get("maxTime");
        return maxTime == null || maxTime.isNull() ? null : maxTime.asDouble();
    }

    private static String nonBlankText(JsonNode value) {
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : null;
    }
}
