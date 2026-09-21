package com.example.backend.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.math.BigDecimal;

/** Guards catalog checksum backfills without changing historical schema definitions. */
public final class SchemaCatalogIntegrity {
    private SchemaCatalogIntegrity() { }

    public static void requireMetadataMatches(String schemaId, String version, String storedName, String storedTopic,
            String catalogName, String catalogTopic) {
        if (!Objects.equals(storedName, catalogName) || !Objects.equals(storedTopic, catalogTopic)) {
            throw new IllegalStateException("Published schema metadata drift detected for " + schemaId + "@" + version
                    + ": stored name/topic differs from the checked-in source catalog. create a new schema version "
                    + "for any intended change.");
        }
    }

    public static boolean definitionsMatch(JsonNode stored, JsonNode catalog) {
        return canonicalize(stored).equals(canonicalize(catalog));
    }

    public static void requireDefinitionsMatch(String schemaId, String version, JsonNode stored, JsonNode catalog) {
        if (!definitionsMatch(stored, catalog)) {
            throw new IllegalStateException("Published schema definition drift detected for "
                    + schemaId + "@" + version
                    + ": the stored definition differs from the checked-in source catalog. Compare the database "
                    + "definition with schemas/source and create a new schema version for any intended change.");
        }
    }

    public static String checksumForVerifiedStoredDefinition(String schemaId, String version, JsonNode stored,
            JsonNode catalog, Function<JsonNode, String> checksum) {
        requireDefinitionsMatch(schemaId, version, stored, catalog);
        // Hash the checked-in canonical source after structural equality is
        // proven. PostgreSQL JSONB may reorder object keys when loading the
        // stored definition, so hashing that hydrated tree can produce a
        // different digest for the same published schema.
        return Objects.requireNonNull(checksum, "checksum function").apply(catalog);
    }

    public static String checksumForVerifiedSolverBinding(String schemaId, String version,
            String storedSolverId, JsonNode storedOutputDefinition, String catalogSolverId,
            JsonNode catalogOutputDefinition, Function<JsonNode, String> checksum) {
        if (!Objects.equals(storedSolverId, catalogSolverId)
                || !definitionsMatch(storedOutputDefinition, catalogOutputDefinition)) {
            throw new IllegalStateException("Published solver binding drift detected for " + schemaId + "@" + version
                    + ": stored solver ID or output contract differs from the checked-in source catalog. "
                    + "create a new schema version for any intended change.");
        }
        return solverBindingChecksum(catalogSolverId, catalogOutputDefinition, checksum);
    }

    public static String solverBindingChecksum(String solverId, JsonNode outputDefinition,
            Function<JsonNode, String> checksum) {
        ObjectNode binding = JsonNodeFactory.instance.objectNode();
        binding.put("solverId", solverId);
        binding.set("outputDefinition", outputDefinition.deepCopy());
        return Objects.requireNonNull(checksum, "checksum function").apply(binding);
    }

    private static JsonNode canonicalize(JsonNode node) {
        if (node == null) return JsonNodeFactory.instance.nullNode();
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            List<String> names = new ArrayList<>();
            node.fieldNames().forEachRemaining(names::add);
            Collections.sort(names);
            for (String name : names) result.set(name, canonicalize(node.get(name)));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            for (JsonNode child : node) result.add(canonicalize(child));
            return result;
        }
        if (node.isNumber()) {
            BigDecimal value = node.decimalValue().stripTrailingZeros();
            if (value.scale() < 0) value = value.setScale(0);
            return JsonNodeFactory.instance.numberNode(value);
        }
        return node.deepCopy();
    }
}
