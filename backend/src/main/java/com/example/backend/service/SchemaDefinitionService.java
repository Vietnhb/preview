package com.example.backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.backend.entity.LifecycleStatus;
import com.example.backend.entity.SchemaVersion;
import com.example.backend.entity.SolverVersion;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.SchemaVersionRepository;
import com.example.backend.repository.SolverVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SchemaDefinitionService {
    public record RequiredGap(String key, String unit) { }
    public record SolverBinding(String numericalSolverId, String referenceSolverId, String version) { }
    private final SchemaVersionRepository repository;
    private final SolverVersionRepository solverRepository;
    private final com.example.backend.repository.TopicRepository topicRepository;

    @Transactional(readOnly = true)
    public SchemaVersion requireApproved(String schemaId) {
        if (!StringUtils.hasText(schemaId)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Schema is missing");
        SchemaVersion schema = repository.findByEnabledTrueAndLifecycleStatus(LifecycleStatus.APPROVED).stream()
                .filter(item -> item.getSchemaId().equalsIgnoreCase(schemaId))
                .max(Comparator.comparing(SchemaVersion::getCreatedAt))
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No approved schema definition exists for: " + schemaId));
        validateDefinition(schema.getDefinition(), schemaId);
        requireEnabledTopic(schema.getTopic());
        return schema;
    }

    @Transactional(readOnly = true)
    public SchemaVersion requireApproved(String schemaId, String version) {
        if (!StringUtils.hasText(schemaId) || !StringUtils.hasText(version)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Persisted schema binding is missing");
        }
        SchemaVersion schema = repository.findFirstBySchemaIdAndVersion(schemaId, version)
                .filter(item -> item.isEnabled() && item.getLifecycleStatus() == LifecycleStatus.APPROVED)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "Persisted schema binding is not approved: " + schemaId + "@" + version));
        validateDefinition(schema.getDefinition(), schemaId);
        requireEnabledTopic(schema.getTopic());
        return schema;
    }

    @Transactional(readOnly = true)
    public List<SchemaVersion> approvedSchemas() {
        var enabledTopics = topicRepository.findAll().stream().filter(com.example.backend.entity.Topic::isEnabled)
                .map(t -> t.getName().toLowerCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        return new java.util.TreeMap<>(repository.findByEnabledTrueAndLifecycleStatus(LifecycleStatus.APPROVED).stream()
                .filter(s -> enabledTopics.contains(s.getTopic().toLowerCase(java.util.Locale.ROOT)))
                .collect(java.util.stream.Collectors.toMap(item -> item.getSchemaId().toLowerCase(), item -> item,
                        (left, right) -> left.getCreatedAt().compareTo(right.getCreatedAt()) >= 0 ? left : right)))
                .values().stream().toList();
    }

    /** Historical rendering is read-only and must survive retirement of a version. */
    @Transactional(readOnly = true)
    public SchemaVersion requireHistorical(String schemaId, String version) {
        return repository.findFirstBySchemaIdAndVersion(schemaId, version)
                .filter(item -> item.getLifecycleStatus() != LifecycleStatus.DRAFT)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Historical schema version not found"));
    }

    private void requireEnabledTopic(String topic) {
        if (topicRepository.findAll().stream().noneMatch(t -> t.isEnabled() && t.getName().equalsIgnoreCase(topic)))
            throw new ApiException(HttpStatus.CONFLICT, "Curriculum topic is disabled: " + topic);
    }

    @Transactional(readOnly = true)
    public SolverBinding requireSolverBinding(String schemaId) {
        SolverVersion solver = solverRepository.findFirstBySchemaIdAndLifecycleStatusOrderByCreatedAtDesc(
                schemaId, LifecycleStatus.APPROVED).orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No approved solver binding exists for: " + schemaId));
        String referenceId = solver.getOutputDefinition().path("referenceSolverId").asText();
        if (!StringUtils.hasText(solver.getSolverId()) || !StringUtils.hasText(referenceId)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Approved solver binding is incomplete: " + schemaId);
        }
        return new SolverBinding(solver.getSolverId(), referenceId, solver.getVersion());
    }

    @Transactional(readOnly = true)
    public SolverBinding requireSolverBinding(String schemaId, String version) {
        SolverVersion solver = solverRepository.findFirstBySchemaIdAndVersion(schemaId, version)
                .filter(item -> item.getLifecycleStatus() == LifecycleStatus.APPROVED)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "Persisted solver binding is not approved: " + schemaId + "@" + version));
        String referenceId = solver.getOutputDefinition().path("referenceSolverId").asText();
        if (!StringUtils.hasText(solver.getSolverId()) || !StringUtils.hasText(referenceId)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Persisted solver binding is incomplete: " + schemaId + "@" + version);
        }
        return new SolverBinding(solver.getSolverId(), referenceId, solver.getVersion());
    }

    public double durationSeconds(JsonNode specification, JsonNode definition) {
        JsonNode execution = definition.path("execution");
        for (JsonNode binding : execution.path("durationBindings")) {
            Set<String> relationTypes = textSet(binding.path("relationTypes"));
            String requiredUnit = binding.path("unit").asText();
            for (JsonNode relation : specification.path("relations")) {
                if (!relationTypes.contains(relation.path("type").asText())) continue;
                JsonNode value = relation.path("value");
                if (!value.isNumber() || !requiredUnit.equals(relation.path("unit").asText())
                        || !Double.isFinite(value.asDouble()) || value.asDouble() <= 0) {
                    throw new ApiException(HttpStatus.CONFLICT,
                            "Persisted simulation duration does not conform to its schema binding");
                }
                return value.asDouble();
            }
        }
        JsonNode configured = execution.path("durationSeconds");
        if (!configured.isNumber() || !Double.isFinite(configured.asDouble()) || configured.asDouble() <= 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Schema execution duration is invalid");
        }
        return configured.asDouble();
    }

    public List<String> validateSpecification(JsonNode specification, JsonNode definition) {
        List<String> blockers = new ArrayList<>();
        JsonNode quantities = specification == null ? null : specification.path("quantities");
        for (JsonNode field : definition.path("requiredQuantities")) {
            JsonNode matched = findQuantity(quantities, names(field));
            String key = field.path("key").asText();
            if (matched == null || !matched.path("normalizedValue").isNumber()) {
                blockers.add("Missing required quantity: " + key);
                continue;
            }
            double value = matched.path("normalizedValue").asDouble();
            if (!Double.isFinite(value) || (field.path("positive").asBoolean(false) && value <= 0)) {
                blockers.add("Invalid value for required quantity: " + key);
            }
            Set<String> units = textSet(field.path("allowedUnits"));
            if (!units.isEmpty() && !units.contains(matched.path("normalizedUnit").asText())) {
                blockers.add("Invalid unit for " + key + ": " + matched.path("normalizedUnit").asText());
            }
        }
        return List.copyOf(blockers);
    }

    public List<RequiredGap> missingRequiredQuantities(JsonNode specification, JsonNode definition) {
        List<RequiredGap> gaps = new ArrayList<>();
        JsonNode quantities = specification == null ? null : specification.path("quantities");
        for (JsonNode field : definition.path("requiredQuantities")) {
            JsonNode matched = findQuantity(quantities, names(field));
            boolean invalid = matched == null || !matched.path("normalizedValue").isNumber()
                    || !Double.isFinite(matched.path("normalizedValue").asDouble())
                    || (field.path("positive").asBoolean(false) && matched.path("normalizedValue").asDouble() <= 0)
                    || (!textSet(field.path("allowedUnits")).isEmpty()
                        && !textSet(field.path("allowedUnits")).contains(matched.path("normalizedUnit").asText()));
            if (invalid) gaps.add(new RequiredGap(field.path("key").asText(),
                    field.path("allowedUnits").isArray() && !field.path("allowedUnits").isEmpty()
                            ? field.path("allowedUnits").get(0).asText() : "SI"));
        }
        return List.copyOf(gaps);
    }

    public Set<String> adjustableKeys(JsonNode definition) {
        Set<String> keys = new HashSet<>();
        for (JsonNode control : definition.path("adjustableParameters")) {
            if (StringUtils.hasText(control.path("key").asText())) keys.add(control.path("key").asText());
        }
        return Set.copyOf(keys);
    }

    public JsonNode visualization(JsonNode definition) {
        ObjectNode presentation = definition.path("visualization").deepCopy();
        presentation.set("controls", definition.path("adjustableParameters"));
        return presentation;
    }

    public void validateDefinition(JsonNode definition, String schemaId) {
        JsonNode execution = definition == null ? null : definition.path("execution");
        if (definition == null || !definition.isObject()
                || !definition.path("requiredQuantities").isArray()
                || !definition.path("adjustableParameters").isArray()
                || !definition.path("visualization").isObject()
                || execution == null || !execution.isObject()
                || !execution.path("durationSeconds").isNumber()
                || !execution.path("stepSeconds").isNumber()
                || !execution.path("durationBindings").isArray()
                || !definition.path("validation").isObject()
                || !StringUtils.hasText(definition.path("model").asText())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Approved schema has an incomplete production definition: " + schemaId);
        }
        double duration = execution.path("durationSeconds").asDouble();
        double step = execution.path("stepSeconds").asDouble();
        JsonNode validation = definition.path("validation");
        if (!(duration > 0) || !(step > 0) || step > duration || duration / step > 1_000_000
                || !validation.path("tolerance").isNumber() || validation.path("tolerance").asDouble() < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Schema timing or validation tolerance is invalid");
        }
    }

    private JsonNode findQuantity(JsonNode quantities, Set<String> acceptedNames) {
        if (quantities == null || !quantities.isArray()) return null;
        for (JsonNode quantity : quantities) {
            String name = quantity.path("name").asText("").toLowerCase();
            String symbol = quantity.path("symbol").asText("").toLowerCase();
            if (acceptedNames.contains(name) || acceptedNames.contains(symbol)) return quantity;
        }
        return null;
    }

    private Set<String> names(JsonNode field) {
        Set<String> names = new HashSet<>();
        names.add(field.path("key").asText().toLowerCase());
        for (JsonNode alias : field.path("aliases")) names.add(alias.asText().toLowerCase());
        return names;
    }

    private Set<String> textSet(JsonNode values) {
        Set<String> result = new HashSet<>();
        if (values.isArray()) values.forEach(value -> result.add(value.asText()));
        return result;
    }
}
