package com.example.backend.ai.extraction;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.HashSet;
import java.util.Set;

/** Validates the provider JSON shape before Jackson binds it to domain records. */
public final class StrictSpecificationValidator {
    private static final Set<String> ROOT_FIELDS = Set.of("schemaVersion", "topic", "schemaId", "objects",
            "quantities", "relations", "endCondition", "confidence", "ambiguities");
    private static final Set<String> OBJECT_FIELDS = Set.of("id", "label", "type");
    private static final Set<String> QUANTITY_FIELDS = Set.of("name", "symbol", "value", "originalUnit",
            "normalizedValue", "normalizedUnit", "confidence", "sourceText");
    private static final Set<String> RELATION_FIELDS = Set.of("type", "subject", "object", "value", "unit", "sourceText");
    private static final Set<String> AMBIGUITY_FIELDS = Set.of("code", "fieldPath", "question", "options");
    private static final Set<String> END_CONDITION_FIELDS = Set.of("type", "duration", "maxTime", "quantity",
            "operator", "value", "count", "event");
    private static final Set<String> EVENT_FIELDS = Set.of("type", "entities", "quantity", "operator", "value",
            "firstQuantity", "secondQuantity", "markerQuantity");
    private static final int MAX_ITEMS = 512;
    private static final int MAX_TEXT = 2_000;

    private StrictSpecificationValidator() {
    }

    public static void validate(JsonNode root) {
        if (root == null || !root.isObject()) fail("root must be an object");
        rejectUnknown(root, ROOT_FIELDS, "root");
        requiredText(root, "schemaVersion");
        if (!"1.0".equals(root.path("schemaVersion").asText())) fail("schemaVersion must be 1.0");
        requiredText(root, "schemaId");
        optionalTextOrNull(root, "topic");
        array(root, "objects");
        array(root, "quantities");
        array(root, "relations");
        array(root, "ambiguities");
        if (!root.path("confidence").isNumber() || !finite(root.path("confidence"))) {
            fail("confidence must be a finite JSON number");
        }
        validateObjects(root.path("objects"));
        validateQuantities(root.path("quantities"));
        validateRelations(root.path("relations"));
        validateAmbiguities(root.path("ambiguities"));
        if (!root.path("endCondition").isObject()) fail("endCondition must be an object");
        validateEndCondition(root.path("endCondition"));
    }

    private static void validateObjects(JsonNode values) {
        limit(values, "objects");
        for (JsonNode item : values) {
            object(item, "object");
            rejectUnknown(item, OBJECT_FIELDS, "object");
            requiredText(item, "id"); requiredText(item, "label"); requiredText(item, "type");
        }
    }

    private static void validateQuantities(JsonNode values) {
        limit(values, "quantities");
        Set<String> names = new HashSet<>();
        for (JsonNode item : values) {
            object(item, "quantity");
            rejectUnknown(item, QUANTITY_FIELDS, "quantity");
            requiredText(item, "name");
            if (!names.add(item.path("name").asText())) fail("duplicate quantity name");
            requiredNumber(item, "value");
            requiredText(item, "originalUnit");
            optionalNumber(item, "normalizedValue");
            optionalNumber(item, "confidence");
            optionalText(item, "symbol"); optionalText(item, "normalizedUnit"); optionalText(item, "sourceText");
        }
    }

    private static void validateRelations(JsonNode values) {
        limit(values, "relations");
        for (JsonNode item : values) {
            object(item, "relation");
            rejectUnknown(item, RELATION_FIELDS, "relation");
            requiredText(item, "type"); requiredText(item, "subject");
            optionalText(item, "object"); optionalText(item, "unit"); optionalText(item, "sourceText");
            JsonNode value = item.get("value");
            if (value != null && !value.isNull() && !value.isNumber() && !value.isTextual() && !value.isBoolean()) {
                fail("relation.value must be number, string, boolean or null");
            }
        }
    }

    private static void validateAmbiguities(JsonNode values) {
        limit(values, "ambiguities");
        Set<String> codes = new HashSet<>();
        Set<String> paths = new HashSet<>();
        for (JsonNode item : values) {
            object(item, "ambiguity");
            rejectUnknown(item, AMBIGUITY_FIELDS, "ambiguity");
            requiredText(item, "code"); requiredText(item, "fieldPath"); requiredText(item, "question");
            if (!codes.add(item.path("code").asText())) fail("duplicate ambiguity code");
            if (!paths.add(item.path("fieldPath").asText())) fail("duplicate ambiguity fieldPath");
            JsonNode options = item.get("options");
            if (options == null || !options.isArray() || options.size() > MAX_ITEMS) fail("ambiguity.options must be an array");
            for (JsonNode option : options) if (!option.isTextual() || option.asText().length() > MAX_TEXT) fail("ambiguity option must be text");
        }
    }

    private static void validateEndCondition(JsonNode condition) {
        rejectUnknown(condition, END_CONDITION_FIELDS, "endCondition");
        requiredText(condition, "type");
        for (String key : Set.of("duration", "maxTime", "value", "count")) {
            JsonNode value = condition.get(key);
            if (value != null && !value.isNull() && (!value.isNumber() || !finite(value))) {
                fail("endCondition." + key + " must be a finite JSON number");
            }
        }
        JsonNode event = condition.get("event");
        if (event != null && !event.isNull()) {
            object(event, "endCondition.event");
            rejectUnknown(event, EVENT_FIELDS, "endCondition.event");
            requiredText(event, "type");
            JsonNode entities = event.get("entities");
            if (entities != null && (!entities.isArray() || entities.size() > MAX_ITEMS)) {
                fail("endCondition.event.entities must be an array");
            }
            for (String key : Set.of("quantity", "firstQuantity", "secondQuantity", "markerQuantity", "operator")) {
                optionalText(event, key);
            }
        }
    }

    private static void rejectUnknown(JsonNode object, Set<String> allowed, String label) {
        Iterator<String> names = object.fieldNames();
        while (names.hasNext()) if (!allowed.contains(names.next())) fail("Unknown field in " + label);
    }

    private static void requiredText(JsonNode object, String key) {
        JsonNode value = object.get(key);
        if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > MAX_TEXT) {
            fail(key + " must be a non-empty text value");
        }
    }

    private static void optionalText(JsonNode object, String key) {
        JsonNode value = object.get(key);
        if (value != null && !value.isNull() && (!value.isTextual() || value.asText().length() > MAX_TEXT)) fail(key + " must be text or null");
    }

    private static void optionalTextOrNull(JsonNode object, String key) {
        JsonNode value = object.get(key);
        if (value != null && !value.isNull() && (!value.isTextual() || value.asText().length() > MAX_TEXT)) {
            fail(key + " must be text or null");
        }
    }

    private static void requiredNumber(JsonNode object, String key) {
        JsonNode value = object.get(key);
        if (value == null || !value.isNumber() || !finite(value)) fail(key + " must be a finite JSON number");
    }

    private static void optionalNumber(JsonNode object, String key) {
        JsonNode value = object.get(key);
        if (value != null && !value.isNull() && (!value.isNumber() || !finite(value))) fail(key + " must be a finite JSON number or null");
    }

    private static void array(JsonNode object, String key) {
        if (!object.path(key).isArray()) fail(key + " must be an array");
    }

    private static void object(JsonNode value, String label) {
        if (value == null || !value.isObject()) fail(label + " must be an object");
    }

    private static void limit(JsonNode value, String label) {
        if (value.size() > MAX_ITEMS) fail(label + " exceeds the item limit");
    }

    private static boolean finite(JsonNode value) {
        return Double.isFinite(value.asDouble());
    }

    private static void fail(String message) {
        throw new IllegalArgumentException("Invalid AI specification: " + message);
    }
}
