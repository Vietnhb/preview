package com.example.backend.schema.routing.index;

import java.text.Normalizer;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.stereotype.Component;

import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.schema.routing.model.SchemaSearchDocument;
import com.example.backend.service.problem.CompiledSchema;
import com.fasterxml.jackson.databind.JsonNode;

/** Projects approved schema metadata into deterministic retrieval fields and text. */
@Component
public final class SchemaSearchDocumentBuilder {
    public SchemaSearchDocument build(SchemaVersion schema, CompiledSchema compiled) {
        if (schema == null || schema.getDefinition() == null || compiled == null) {
            throw new IllegalArgumentException("Approved schema identity, compiled schema, and definition are required");
        }
        String checksum = schema.getDefinitionChecksum();
        if (checksum == null || checksum.isBlank()) {
            throw new IllegalStateException("Schema " + schema.getSchemaId() + "@" + schema.getVersion()
                    + " has no verified source checksum");
        }
        if (!schema.getSchemaId().equals(compiled.schemaId())
                || !schema.getVersion().equals(compiled.version())
                || !schema.getTopic().equals(compiled.topic())
                || !checksum.equals(compiled.checksum())) {
            throw new IllegalArgumentException("Compiled schema identity/checksum does not match "
                    + schema.getSchemaId() + "@" + schema.getVersion());
        }

        JsonNode definition = schema.getDefinition();
        String description = text(definition.get("description"));
        TreeSet<String> learningOutcomes = new TreeSet<>();
        collectText(definition.get("learningOutcomes"), learningOutcomes);
        TreeSet<String> canonicalKeys = new TreeSet<>();
        TreeSet<String> aliases = new TreeSet<>();
        TreeSet<String> symbols = new TreeSet<>();
        TreeSet<String> allowedUnits = new TreeSet<>();
        compiled.quantities().values().forEach(quantity -> {
            canonicalKeys.add(quantity.key());
            aliases.addAll(quantity.aliases());
            symbols.addAll(quantity.symbols());
            allowedUnits.addAll(quantity.allowedUnits());
        });
        collectAdjustmentSymbols(definition.get("adjustableParameters"), canonicalKeys, symbols);

        TreeSet<String> relationTypes = relationTypes(definition);
        TreeSet<String> endConditionCapabilities = endConditionCapabilities(definition);
        TreeSet<String> curriculumLabels = curriculumLabels(definition);
        TreeSet<String> metadata = new TreeSet<>();
        add(metadata, schema.getSchemaId());
        add(metadata, schema.getVersion());
        add(metadata, schema.getName());
        add(metadata, schema.getTopic());
        add(metadata, compiled.modelId());
        add(metadata, description);
        metadata.addAll(learningOutcomes);
        metadata.addAll(canonicalKeys);
        metadata.addAll(aliases);
        metadata.addAll(symbols);
        metadata.addAll(allowedUnits);
        metadata.addAll(relationTypes);
        metadata.addAll(endConditionCapabilities);
        metadata.addAll(curriculumLabels);

        String searchText = Normalizer.normalize(String.join(" ", metadata), Normalizer.Form.NFKC);
        return new SchemaSearchDocument(schema.getSchemaId(), schema.getVersion(), schema.getTopic(),
                schema.getName(), compiled.modelId(), description, learningOutcomes, canonicalKeys, aliases,
                symbols, allowedUnits, relationTypes, endConditionCapabilities, curriculumLabels,
                searchText, checksum);
    }

    private TreeSet<String> relationTypes(JsonNode definition) {
        TreeSet<String> result = new TreeSet<>();
        collectRelationTypes(definition.get("relationTypes"), result);
        collectRelationTypes(definition.path("relationContract").get("relationTypes"), result);
        JsonNode bindings = definition.path("execution").path("durationBindings");
        if (bindings.isArray()) {
            for (JsonNode binding : bindings) collectRelationTypes(binding.get("relationTypes"), result);
        }
        return result;
    }

    private TreeSet<String> endConditionCapabilities(JsonNode definition) {
        TreeSet<String> result = new TreeSet<>();
        collectText(definition.get("endConditionCapabilities"), result);
        collectText(definition.path("execution").get("endConditionCapabilities"), result);
        JsonNode endCondition = definition.get("endCondition");
        collectText(endCondition == null ? null : endCondition.get("capabilities"), result);
        if (endCondition != null) collectText(endCondition.get("type"), result);
        collectEndConditionTypes(definition.get("endConditions"), result);
        return result;
    }

    private TreeSet<String> curriculumLabels(JsonNode definition) {
        TreeSet<String> result = new TreeSet<>();
        collectText(definition.get("curriculumLabels"), result);
        collectText(definition.get("curriculumLabel"), result);
        collectText(definition.path("curriculum").get("moduleLabels"), result);
        collectText(definition.path("curriculum").get("lessonLabels"), result);
        return result;
    }

    private void collectAdjustmentSymbols(JsonNode nodes, Set<String> keys, Set<String> symbols) {
        if (nodes == null || !nodes.isArray()) return;
        for (JsonNode parameter : nodes) {
            add(keys, parameter.get("key"));
            add(symbols, parameter.get("symbol"));
        }
    }

    private void collectRelationTypes(JsonNode values, Set<String> destination) {
        if (values == null || values.isNull()) return;
        if (values.isTextual()) add(destination, values.asText());
        else if (values.isArray()) values.forEach(value -> add(destination, value));
    }

    private void collectEndConditionTypes(JsonNode values, Set<String> destination) {
        if (values == null || values.isNull()) return;
        if (values.isTextual()) {
            add(destination, values.asText());
        } else if (values.isArray()) {
            for (JsonNode condition : values) {
                if (condition.isTextual()) add(destination, condition.asText());
                else add(destination, condition.get("type"));
            }
        }
    }

    private void collectText(JsonNode values, Set<String> destination) {
        if (values == null || values.isNull()) return;
        if (values.isTextual()) add(destination, values.asText());
        else if (values.isArray()) values.forEach(value -> add(destination, value));
    }

    private void add(Set<String> destination, JsonNode value) {
        if (value != null && value.isTextual()) add(destination, value.asText());
    }

    private void add(Set<String> destination, String value) {
        if (value != null && !value.isBlank()) destination.add(value.trim());
    }

    private String text(JsonNode value) {
        return value != null && value.isTextual() ? value.asText().trim() : "";
    }
}
