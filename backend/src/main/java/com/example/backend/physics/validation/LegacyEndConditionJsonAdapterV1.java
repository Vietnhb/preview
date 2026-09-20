package com.example.backend.physics.validation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Explicit V1 bridge for persisted historical end-condition JSON contracts. */
public final class LegacyEndConditionJsonAdapterV1 {
    private LegacyEndConditionJsonAdapterV1() { }

    public static EndConditionContract compile(JsonNode condition, double fallbackDuration) {
        if (condition == null || condition.isNull())
            return new EndConditionContract.TimeLimit(safeDuration(fallbackDuration));
        List<String> errors = EndConditionResolver.validateNode(condition, fallbackDuration);
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
        String rawType = condition.path("type").asText("");
        EndConditionType type = EndConditionType.fromWireName(rawType);
        if (type == null) throw new IllegalArgumentException("Unsupported V1 legacy endCondition type: " + rawType);
        return switch (type) {
            case TIME_LIMIT -> new EndConditionContract.TimeLimit(condition.path("duration").asDouble());
            case THRESHOLD -> new EndConditionContract.Threshold(
                    OutputSourceBinding.fromLegacy(condition.path("quantity").asText()),
                    ComparisonOperator.parse(condition.path("operator").asText()),
                    condition.path("value").asDouble(), optionalMaxTime(condition));
            case EVENT -> compileEvent(condition);
            case CYCLE_COUNT -> new EndConditionContract.CycleCount(
                    OutputSourceBinding.fromLegacy(condition.path("quantity").asText()),
                    condition.path("count").asInt(), optionalMaxTime(condition));
            case MANUAL -> new EndConditionContract.Manual(optionalMaxTime(condition));
        };
    }

    private static EndConditionContract.Event compileEvent(JsonNode condition) {
        JsonNode event = condition.path("event");
        String rawKind = event.path("type").asText("").trim().toLowerCase(Locale.ROOT);
        EndConditionContract.EventKind kind = switch (rawKind) {
            case "contact" -> EndConditionContract.EventKind.CONTACT;
            case "collision" -> EndConditionContract.EventKind.COLLISION;
            default -> throw new IllegalArgumentException("Unsupported V1 legacy event type: " + rawKind);
        };
        List<String> entities = new ArrayList<>();
        event.path("entities").forEach(entity -> { if (entity.isTextual()) entities.add(entity.asText()); });
        String marker = nonBlankText(event.get("markerQuantity"));
        String first = nonBlankText(event.get("firstQuantity"));
        String second = nonBlankText(event.get("secondQuantity"));
        if (kind == EndConditionContract.EventKind.COLLISION && first == null && second == null && marker == null
                && entities.size() >= 2) {
            first = entities.get(0);
            second = entities.get(1);
            return new EndConditionContract.Event(kind, entities, null, null, null,
                    new OutputSourceBinding(OutputSourceBinding.Group.LEGACY_ENTITY_POSITION, first),
                    new OutputSourceBinding(OutputSourceBinding.Group.LEGACY_ENTITY_POSITION, second),
                    null, optionalMaxTime(condition));
        }
        String quantity = nonBlankText(event.get("quantity"));
        ComparisonOperator operator = ComparisonOperator.parse(nonBlankText(event.get("operator")));
        Double value = event.path("value").isNumber() ? event.path("value").asDouble() : null;
        return new EndConditionContract.Event(kind, entities,
                quantity == null ? null : OutputSourceBinding.fromLegacy(quantity), operator, value,
                first == null ? null : OutputSourceBinding.fromLegacy(first),
                second == null ? null : OutputSourceBinding.fromLegacy(second),
                marker == null ? null : OutputSourceBinding.fromLegacy(marker), optionalMaxTime(condition));
    }

    private static Double optionalMaxTime(JsonNode condition) {
        JsonNode maxTime = condition.get("maxTime");
        return maxTime == null || maxTime.isNull() ? null : maxTime.asDouble();
    }

    private static String nonBlankText(JsonNode value) {
        return value != null && value.isTextual() && !value.asText().isBlank() ? value.asText().trim() : null;
    }

    private static double safeDuration(double duration) {
        return Double.isFinite(duration) && duration > 0 ? duration : 0.01;
    }
}
