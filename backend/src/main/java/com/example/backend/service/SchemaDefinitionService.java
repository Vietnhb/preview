package com.example.backend.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.backend.entity.LifecycleStatus;
import com.example.backend.entity.SchemaVersion;
import com.example.backend.entity.SolverVersion;
import com.example.backend.exception.ApiException;
import com.example.backend.physics.EndConditionResolver;
import com.example.backend.repository.SchemaVersionRepository;
import com.example.backend.repository.SolverVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SchemaDefinitionService {
    private static final String DURATION_SECONDS = "durationSeconds";
    private static final String QUANTITIES = "quantities";
    private static final String REQUIRED_QUANTITIES = "requiredQuantities";
    private static final String NORMALIZED_VALUE = "normalizedValue";
    private static final String ALLOWED_UNITS = "allowedUnits";
    private static final String NORMALIZED_UNIT = "normalizedUnit";
    private static final String ADJUSTABLE_PARAMETERS = "adjustableParameters";
    public record RequiredGap(String key, String unit) { }
    public record SolverBinding(String numericalSolverId, String referenceSolverId, String version) { }
    public record AdjustableParameter(String key, double min, double max) { }
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
        JsonNode configured = execution.path(DURATION_SECONDS);
        if (!configured.isNumber() || !Double.isFinite(configured.asDouble()) || configured.asDouble() <= 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Schema execution duration is invalid");
        }
        return configured.asDouble();
    }

    public List<String> validateSpecification(JsonNode specification, JsonNode definition) {
        List<String> blockers = new ArrayList<>();
        JsonNode quantities = specification == null ? null : specification.path(QUANTITIES);
        for (JsonNode field : definition.path(REQUIRED_QUANTITIES)) {
            JsonNode matched = findQuantity(quantities, names(field));
            String key = field.path("key").asText();
            if (matched == null || !matched.path(NORMALIZED_VALUE).isNumber()) {
                blockers.add("Missing required quantity: " + key);
                continue;
            }
            double value = matched.path(NORMALIZED_VALUE).asDouble();
            if (!Double.isFinite(value) || (field.path("positive").asBoolean(false) && value <= 0)) {
                blockers.add("Invalid value for required quantity: " + key);
            }
            Set<String> units = textSet(field.path(ALLOWED_UNITS));
            if (!units.isEmpty() && !units.contains(matched.path(NORMALIZED_UNIT).asText())) {
                blockers.add("Invalid unit for " + key + ": " + matched.path(NORMALIZED_UNIT).asText());
            }
        }
        double fallbackDuration = definition.path("execution").path(DURATION_SECONDS).asDouble(10);
        blockers.addAll(EndConditionResolver.validate(specification, fallbackDuration));
        return List.copyOf(blockers);
    }

    public List<RequiredGap> missingRequiredQuantities(JsonNode specification, JsonNode definition) {
        List<RequiredGap> gaps = new ArrayList<>();
        JsonNode quantities = specification == null ? null : specification.path(QUANTITIES);
        for (JsonNode field : definition.path(REQUIRED_QUANTITIES)) {
            JsonNode matched = findQuantity(quantities, names(field));
            boolean invalid = matched == null || !matched.path(NORMALIZED_VALUE).isNumber()
                    || !Double.isFinite(matched.path(NORMALIZED_VALUE).asDouble())
                    || (field.path("positive").asBoolean(false) && matched.path(NORMALIZED_VALUE).asDouble() <= 0)
                    || (!textSet(field.path(ALLOWED_UNITS)).isEmpty()
                        && !textSet(field.path(ALLOWED_UNITS)).contains(matched.path(NORMALIZED_UNIT).asText()));
            if (invalid) gaps.add(new RequiredGap(field.path("key").asText(),
                    field.path(ALLOWED_UNITS).isArray() && !field.path(ALLOWED_UNITS).isEmpty()
                            ? field.path(ALLOWED_UNITS).get(0).asText() : "SI"));
        }
        return List.copyOf(gaps);
    }

    public Set<String> adjustableKeys(JsonNode definition) {
        return adjustableParameters(definition).stream()
                .map(AdjustableParameter::key)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public List<AdjustableParameter> adjustableParameters(JsonNode definition) {
        List<AdjustableParameter> controls = new ArrayList<>();
        for (JsonNode control : definition.path(ADJUSTABLE_PARAMETERS)) {
            String key = control.path("key").asText("").trim();
            if (!StringUtils.hasText(key) || !control.path("min").isNumber() || !control.path("max").isNumber()) continue;
            double min = control.path("min").asDouble();
            double max = control.path("max").asDouble();
            if (Double.isFinite(min) && Double.isFinite(max) && min <= max) {
                controls.add(new AdjustableParameter(key, min, max));
            }
        }
        return List.copyOf(controls);
    }

    /** Builds the complete snapshot used by a solver without silently defaulting values. */
    public Map<String, Double> effectiveAdjustments(JsonNode specification, JsonNode definition,
            Map<String, Double> requested, Map<String, Double> previous) {
        Map<String, Double> supplied = new LinkedHashMap<>();
        if (previous != null) supplied.putAll(previous);
        if (requested != null) supplied.putAll(requested);
        Map<String, Double> effective = new LinkedHashMap<>();
        for (AdjustableParameter control : adjustableParameters(definition)) {
            Double value = supplied.get(control.key());
            if (value == null) {
                JsonNode quantity = findQuantityForControl(specification, definition, control.key());
                if (quantity != null && quantity.path(NORMALIZED_VALUE).isNumber()) {
                    value = quantity.path(NORMALIZED_VALUE).asDouble();
                }
            }
            if (value == null || !Double.isFinite(value)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Missing or invalid adjustable parameter: " + control.key());
            }
            if (value < control.min() || value > control.max()) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Parameter " + control.key() + " must be between " + control.min() + " and " + control.max());
            }
            effective.put(control.key(), value);
        }
        return Map.copyOf(effective);
    }

    public JsonNode visualization(JsonNode definition) {
        ObjectNode presentation = definition.path("visualization").deepCopy();
        presentation.set("controls", definition.path(ADJUSTABLE_PARAMETERS));
        return presentation;
    }

    public void validateDefinition(JsonNode definition, String schemaId) {
        JsonNode execution = definition == null ? null : definition.path("execution");
        if (definition == null || !definition.isObject()
                || !definition.path(REQUIRED_QUANTITIES).isArray()
                || !definition.path(ADJUSTABLE_PARAMETERS).isArray()
                || !definition.path("visualization").isObject()
                || execution == null || !execution.isObject()
                || !execution.path(DURATION_SECONDS).isNumber()
                || !execution.path("stepSeconds").isNumber()
                || !execution.path("durationBindings").isArray()
                || !definition.path("validation").isObject()
                || !StringUtils.hasText(definition.path("model").asText())) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Approved schema has an incomplete production definition: " + schemaId);
        }
        double duration = execution.path(DURATION_SECONDS).asDouble();
        double step = execution.path("stepSeconds").asDouble();
        JsonNode validation = definition.path("validation");
        if (duration <= 0 || step <= 0 || step > duration || duration / step > 1_000_000
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

    private JsonNode findQuantityForControl(JsonNode specification, JsonNode definition, String key) {
        for (JsonNode field : definition.path(REQUIRED_QUANTITIES)) {
            if (key.equalsIgnoreCase(field.path("key").asText())) {
                return findQuantity(specification == null ? null : specification.path(QUANTITIES), names(field));
            }
        }
        return findQuantity(specification == null ? null : specification.path(QUANTITIES), Set.of(key.toLowerCase()));
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
