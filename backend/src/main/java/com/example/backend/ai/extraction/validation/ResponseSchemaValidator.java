package com.example.backend.ai.extraction.validation;

import com.fasterxml.jackson.databind.JsonNode;

/** Validates the structural JSON Schema vocabulary used by provider response contracts.
 * No text interpretation or domain decisions belong at this boundary. */
public final class ResponseSchemaValidator {
    private ResponseSchemaValidator() { }

    public static void validate(JsonNode value, JsonNode schema) {
        validate(value, schema, schema, "$" );
    }

    private static void validate(JsonNode value, JsonNode schema, JsonNode root, String path) {
        if (validateReference(value, schema, root, path)) return;
        if (schema.has("anyOf")) {
            validateAnyOf(value, schema, root, path);
            return;
        }
        validateType(value, schema.path("type"), path);
        validateValueConstraints(value, schema, path);
        if (value == null || value.isNull()) return;
        validateObject(value, schema, root, path);
        validateArray(value, schema, root, path);
        validateFiniteNumber(value, path);
    }

    private static boolean validateReference(JsonNode value, JsonNode schema, JsonNode root, String path) {
        if (!schema.has("$ref")) return false;
        String reference = schema.path("$ref").asText();
        if (!reference.startsWith("#/")) fail(path, "only local schema references are supported");
        JsonNode target = root;
        for (String part : reference.substring(2).split("/")) {
            target = target.path(part.replace("~1", "/").replace("~0", "~"));
        }
        if (target.isMissingNode()) fail(path, "schema reference does not exist: " + reference);
        validate(value, target, root, path);
        return true;
    }

    private static void validateAnyOf(JsonNode value, JsonNode schema, JsonNode root, String path) {
        for (JsonNode alternative : schema.path("anyOf")) {
            if (matchesAlternative(value, alternative, root, path)) return;
        }
        fail(path, "does not match any allowed shape");
    }

    private static boolean matchesAlternative(JsonNode value, JsonNode alternative, JsonNode root, String path) {
        try {
            validate(value, alternative, root, path);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static void validateType(JsonNode value, JsonNode type, String path) {
        if (type.isTextual() && !matches(value, type.asText())) fail(path, "expected " + type.asText());
        if (!type.isArray()) return;
        boolean accepted = false;
        for (JsonNode candidate : type) accepted |= matches(value, candidate.asText());
        if (!accepted) fail(path, "unexpected JSON type");
    }

    private static void validateValueConstraints(JsonNode value, JsonNode schema, String path) {
        if (schema.has("const") && !schema.get("const").equals(value)) fail(path, "invalid constant");
        if (!schema.has("enum")) return;
        boolean accepted = false;
        for (JsonNode candidate : schema.get("enum")) accepted |= candidate.equals(value);
        if (!accepted) fail(path, "value is outside the allowed enum");
    }

    private static void validateObject(JsonNode value, JsonNode schema, JsonNode root, String path) {
        if (!value.isObject()) return;
        for (JsonNode key : schema.path("required")) {
            if (!value.has(key.asText())) fail(path + "." + key.asText(), "required field is absent");
        }
        var fields = value.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            JsonNode fieldSchema = schema.path("properties").get(field.getKey());
            if (fieldSchema != null) {
                validate(field.getValue(), fieldSchema, root, path + "." + field.getKey());
            } else if (schema.path("additionalProperties").isBoolean()
                    && !schema.path("additionalProperties").asBoolean()) {
                fail(path + "." + field.getKey(), "unknown field");
            }
        }
    }

    private static void validateArray(JsonNode value, JsonNode schema, JsonNode root, String path) {
        if (!value.isArray() || !schema.has("items")) return;
        for (int i = 0; i < value.size(); i++) {
            validate(value.get(i), schema.get("items"), root, path + "[" + i + "]");
        }
    }

    private static void validateFiniteNumber(JsonNode value, String path) {
        if (value.isNumber() && !Double.isFinite(value.asDouble())) fail(path, "number must be finite");
    }

    private static boolean matches(JsonNode value, String type) {
        if (value == null) return false;
        return switch (type) {
            case "null" -> value.isNull();
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "number" -> value.isNumber();
            case "integer" -> value.isIntegralNumber();
            case "boolean" -> value.isBoolean();
            default -> throw new IllegalArgumentException("Unsupported response contract type: " + type);
        };
    }

    private static void fail(String path, String reason) {
        throw new IllegalArgumentException(path + ": " + reason);
    }
}
