package com.example.backend.service.problem;

import com.example.backend.entity.curriculum.Topic;
import com.example.backend.repository.curriculum.TopicRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.backend.entity.enums.LifecycleStatus;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.entity.simulation.SolverVersion;
import com.example.backend.exception.ApiException;
import com.example.backend.exception.SchemaCompilationException;
import com.example.backend.exception.SolverBindingException;
import com.example.backend.physics.validation.EndConditionResolver;
import com.example.backend.physics.compatibility.LegacySchemaIdentityAdapter;
import com.example.backend.repository.problem.SchemaVersionRepository;
import com.example.backend.repository.simulation.SolverVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SchemaDefinitionService {
    private static final Set<String> SCENE_PRIMITIVES = Set.of("background", "grid", "environment", "ruler", "body",
            "circle", "rectangle", "line", "arrow", "vector", "trajectory", "spring", "rope", "waveField",
            "prop", "effect", "text", "graph", "chart", "circuitComponent", "vectorScene");
    private static final Set<String> SCENE_EFFECTS = Set.of("motion.trail", "vehicle.headlight", "vehicle.brake-smoke",
            "projectile.glow", "collision.flash", "circuit.current-flow", "circuit.capacitor-glow");
    private static final Set<String> VECTOR_SHAPES = Set.of("path", "ellipse", "rect", "text", "group");
    private static final Map<String, Integer> PATH_ARITY = Map.of("M", 2, "L", 2, "Q", 4, "C", 6, "Z", 0);
    private static final Set<String> EXPRESSION_OPERATORS = Set.of("add", "subtract", "multiply", "divide", "min",
            "max", "abs", "negate", "sin", "cos", "clamp");
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
    private final com.example.backend.repository.curriculum.TopicRepository topicRepository;
    private final SchemaCompiler schemaCompiler = new SchemaCompiler(new ObjectMapper());
    private final Map<String, CompiledSchema> compiledCache = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public SchemaVersion requireApproved(String schemaId) {
        if (!StringUtils.hasText(schemaId)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Schema is missing");
        String exactSchemaId = schemaId.trim();
        SchemaVersion schema = repository.findAllBySchemaIdIgnoreCaseAndLifecycleStatusOrderByCreatedAtDesc(
                exactSchemaId, LifecycleStatus.APPROVED).stream()
                .reduce(SchemaVersionOrdering::newer)
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No approved schema definition exists for: " + exactSchemaId));
        validateDefinition(schema.getDefinition(), schema.getSchemaId(), schema.getVersion(), schema.getTopic());
        requireEnabledTopic(schema.getTopic());
        compiled(schema);
        return schema;
    }

    @Transactional(readOnly = true)
    public SchemaVersion requireCurrentApproved(String schemaId, String version) {
        if (!StringUtils.hasText(schemaId) || !StringUtils.hasText(version)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Routed schema identity is missing");
        }
        String exactSchemaId = schemaId.trim();
        SchemaVersion schema = repository.findFirstBySchemaIdAndVersion(exactSchemaId, version.trim())
                .filter(item -> item.getLifecycleStatus() == LifecycleStatus.APPROVED)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "Routed schema is no longer approved: " + exactSchemaId + "@" + version));
        List<String> enabledTopics = topicRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                .map(Topic::getName).toList();
        if (enabledTopics.stream().noneMatch(topic -> topic.equalsIgnoreCase(schema.getTopic()))) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Routed schema topic is disabled: " + schema.getSchemaId() + "@" + schema.getVersion());
        }
        SchemaVersion latest = repository.findAllBySchemaIdIgnoreCaseAndLifecycleStatusAndTopicInOrderByCreatedAtDesc(
                        schema.getSchemaId(), LifecycleStatus.APPROVED, enabledTopics).stream()
                .reduce(SchemaVersionOrdering::newer)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "No current approved schema version exists for: " + schema.getSchemaId()));
        if (!latest.getVersion().equals(schema.getVersion())) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Routed schema version is stale; current version is " + latest.getSchemaId() + "@" + latest.getVersion());
        }
        validateDefinition(schema.getDefinition(), schema.getSchemaId(), schema.getVersion(), schema.getTopic());
        requireEnabledTopic(schema.getTopic());
        compiled(schema);
        return schema;
    }

    /**
     * Reports whether a pinned identity is the latest approved version within
     * enabled curriculum topics. This is lifecycle data, not a model/schema
     * dispatch rule, and is used to prevent legacy runtime fallback for current
     * production schemas.
     */
    @Transactional(readOnly = true)
    public boolean isLatestApprovedEnabledVersion(String schemaId, String version) {
        if (!StringUtils.hasText(schemaId) || !StringUtils.hasText(version)) return false;
        List<String> enabledTopics = topicRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                .map(Topic::getName).toList();
        if (enabledTopics.isEmpty()) return false;
        SchemaVersion latest = repository
                .findAllBySchemaIdIgnoreCaseAndLifecycleStatusAndTopicInOrderByCreatedAtDesc(
                        schemaId.trim(), LifecycleStatus.APPROVED, enabledTopics).stream()
                .reduce(SchemaVersionOrdering::newer)
                .orElse(null);
        return latest != null && latest.getVersion().equals(version.trim());
    }

    /** Resolves a version already pinned into persisted work; retired versions remain replayable. */
    @Transactional(readOnly = true)
    public SchemaVersion requirePublishedVersion(String schemaId, String version) {
        if (!StringUtils.hasText(schemaId) || !StringUtils.hasText(version)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Persisted schema binding is missing");
        }
        String exactSchemaId = schemaId.trim();
        SchemaVersion schema = findPublishedIdentityForReplay(exactSchemaId, version.trim())
                .filter(item -> item.getLifecycleStatus() != LifecycleStatus.DRAFT)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "Persisted schema binding is unavailable for replay: " + exactSchemaId + "@" + version));
        validateDefinition(schema.getDefinition(), schema.getSchemaId(), schema.getVersion(), schema.getTopic());
        requireEnabledTopic(schema.getTopic());
        compiled(schema);
        return schema;
    }

    @Transactional(readOnly = true)
    public List<SchemaVersion> approvedSchemas() {
        List<String> enabledTopics = topicRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                .map(Topic::getName).toList();
        if (enabledTopics.isEmpty()) return List.of();
        java.util.Map<String, SchemaVersion> latestBySchemaId = repository
                .findAllByLifecycleStatusAndTopicInOrderByTopicAscSchemaIdAscCreatedAtDesc(
                        LifecycleStatus.APPROVED, enabledTopics).stream()
                .collect(java.util.stream.Collectors.toMap(
                        item -> item.getSchemaId().toLowerCase(), item -> item, SchemaVersionOrdering::newer));
        return new java.util.TreeMap<>(latestBySchemaId).values().stream().toList();
    }

    /** Historical rendering is read-only and must survive retirement of a version. */
    @Transactional(readOnly = true)
    public SchemaVersion requireHistorical(String schemaId, String version) {
        if (!StringUtils.hasText(schemaId) || !StringUtils.hasText(version)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Historical schema version not found");
        }
        return findPublishedIdentityForReplay(schemaId.trim(), version.trim())
                .filter(item -> item.getLifecycleStatus() != LifecycleStatus.DRAFT)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Historical schema version not found"));
    }

    private java.util.Optional<SchemaVersion> findPublishedIdentityForReplay(String schemaId, String version) {
        java.util.Optional<SchemaVersion> exact = repository.findFirstBySchemaIdAndVersion(schemaId, version);
        if (exact.isPresent()) return exact;
        String adapted = LegacySchemaIdentityAdapter.adaptForReplay(
                schemaId, LegacySchemaIdentityAdapter.VERSION);
        return adapted.equals(schemaId) ? java.util.Optional.empty()
                : repository.findFirstBySchemaIdAndVersion(adapted, version);
    }

    private void requireEnabledTopic(String topic) {
        if (!topicRepository.existsByNameIgnoreCaseAndEnabledTrue(topic))
            throw new ApiException(HttpStatus.CONFLICT, "Curriculum topic is disabled: " + topic);
    }

    @Transactional(readOnly = true)
    public SolverBinding requireSolverBinding(String schemaId) {
        String exactSchemaId = schemaId == null ? "" : schemaId.trim();
        SolverVersion solver = solverRepository.findFirstBySchemaIdAndLifecycleStatusOrderByCreatedAtDesc(
                exactSchemaId, LifecycleStatus.APPROVED).orElseThrow(() -> new SolverBindingException(
                        "No approved solver binding exists for schemaId=" + exactSchemaId));
        String referenceId = solver.getOutputDefinition().path("referenceSolverId").asText();
        if (!StringUtils.hasText(solver.getSolverId()) || !StringUtils.hasText(referenceId)) {
            throw new SolverBindingException("Approved solver binding is incomplete for schemaId=" + exactSchemaId);
        }
        return new SolverBinding(solver.getSolverId(), referenceId, solver.getVersion());
    }

    public String compiledChecksum(JsonNode definition) {
        return schemaCompiler.checksum(definition);
    }

    public CompiledSchema compiled(SchemaVersion schema) {
        if (schema == null || !StringUtils.hasText(schema.getSchemaId()) || !StringUtils.hasText(schema.getVersion())
                || !StringUtils.hasText(schema.getTopic())) {
            throw new SchemaCompilationException("Schema compiler requires schemaId, version, and topic identity");
        }
        String checksum = schemaCompiler.checksum(schema.getDefinition());
        if (StringUtils.hasText(schema.getDefinitionChecksum()) && !schema.getDefinitionChecksum().equals(checksum)) {
            throw new SchemaCompilationException("Stored schema checksum does not match definition for "
                    + schema.getSchemaId() + "@" + schema.getVersion());
        }
        String cacheKey = schema.getSchemaId() + "@" + schema.getVersion() + "@" + checksum;
        return compiledCache.computeIfAbsent(cacheKey,
                ignored -> schemaCompiler.compile(schema.getDefinition(), schema.getSchemaId(),
                        schema.getVersion(), schema.getTopic()));
    }

    @Transactional(readOnly = true)
    public SolverBinding requireSolverBinding(String schemaId, String version) {
        String exactSchemaId = schemaId == null ? "" : schemaId.trim();
        SolverVersion solver = solverRepository.findFirstBySchemaIdAndVersion(exactSchemaId, version)
                .filter(item -> item.getLifecycleStatus() != LifecycleStatus.DRAFT)
                .orElseThrow(() -> new SolverBindingException("Persisted solver binding is unavailable for runtime: "
                        + exactSchemaId + "@" + version));
        String referenceId = solver.getOutputDefinition().path("referenceSolverId").asText();
        if (!StringUtils.hasText(solver.getSolverId()) || !StringUtils.hasText(referenceId)) {
            throw new SolverBindingException("Persisted solver binding is incomplete: " + exactSchemaId + "@" + version);
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
            if (invalidNumericValue(field, value)) {
                blockers.add("Invalid value for required quantity: " + key);
            }
            Set<String> units = textSet(field.path(ALLOWED_UNITS));
            if (!units.isEmpty() && !units.contains(matched.path(NORMALIZED_UNIT).asText())) {
                blockers.add("Invalid unit for " + key + ": " + matched.path(NORMALIZED_UNIT).asText());
            }
            if (!sameUnitAsReference(quantities, field, matched)) {
                blockers.add("Unit for " + key + " must match " + field.path("sameUnitAs").asText());
            }
        }
        for (JsonNode field : definition.path("optionalQuantities")) {
            JsonNode matched = findQuantity(quantities, names(field));
            if (matched == null) continue;
            String key = field.path("key").asText();
            double value = matched.path(NORMALIZED_VALUE).asDouble(Double.NaN);
            if (invalidNumericValue(field, value)) {
                blockers.add("Invalid value for optional quantity: " + key);
            }
            Set<String> units = textSet(field.path(ALLOWED_UNITS));
            if (!units.isEmpty() && !units.contains(matched.path(NORMALIZED_UNIT).asText())) {
                blockers.add("Invalid unit for " + key + ": " + matched.path(NORMALIZED_UNIT).asText());
            }
            if (!sameUnitAsReference(quantities, field, matched)) {
                blockers.add("Unit for " + key + " must match " + field.path("sameUnitAs").asText());
            }
        }
        double fallbackDuration = definition.path("execution").path(DURATION_SECONDS).asDouble();
        blockers.addAll(EndConditionResolver.validate(specification, fallbackDuration));
        blockers.addAll(validateEndConditionBindings(specification, definition));
        return List.copyOf(blockers);
    }

    private List<String> validateEndConditionBindings(JsonNode specification, JsonNode definition) {
        JsonNode condition = EndConditionResolver.normalize(specification,
                definition.path("execution").path(DURATION_SECONDS).asDouble());
        Set<String> declared = new HashSet<>();
        Set<String> scalarOutputs = new HashSet<>();
        for (JsonNode output : definition.path("output").path("probeSeries")) {
            if (output.isTextual()) declared.add(output.asText());
        }
        for (JsonNode output : definition.path("output").path("definitions")) {
            String key = output.path("key").asText("").trim();
            if (!key.isBlank()) declared.add(key);
            if ("scalar".equalsIgnoreCase(output.path("kind").asText("")) && !key.isBlank()) {
                scalarOutputs.add(key);
            }
        }
        for (JsonNode series : definition.path("visualization").path("series")) {
            if (series.path("key").isTextual()) declared.add(series.path("key").asText());
        }
        List<String> errors = new ArrayList<>();
        if (!condition.isObject()) return errors;
        addBindingError(errors, declared, scalarOutputs, condition.path("quantity").asText(""), "endCondition.quantity");
        JsonNode event = condition.path("event");
        if (event.isObject()) {
            addBindingError(errors, declared, scalarOutputs, event.path("quantity").asText(""), "endCondition.event.quantity");
            addBindingError(errors, declared, scalarOutputs, event.path("firstQuantity").asText(""), "endCondition.event.firstQuantity");
            addBindingError(errors, declared, scalarOutputs, event.path("secondQuantity").asText(""), "endCondition.event.secondQuantity");
            addBindingError(errors, declared, scalarOutputs, event.path("markerQuantity").asText(""), "endCondition.event.markerQuantity");
        }
        return errors;
    }

    private void addBindingError(List<String> errors, Set<String> declared, Set<String> scalarOutputs,
            String key, String path) {
        if (key == null || key.isBlank()) return;
        String normalized = key.contains(".") ? key.substring(key.lastIndexOf('.') + 1) : key;
        if (!declared.contains(key) && !declared.contains(normalized)) {
            errors.add(path + " is not declared by the schema output contract: " + key);
        } else if (scalarOutputs.contains(key) || scalarOutputs.contains(normalized)) {
            errors.add(path + " cannot reference a scalar output: " + key);
        }
    }

    public List<RequiredGap> missingRequiredQuantities(JsonNode specification, JsonNode definition) {
        List<RequiredGap> gaps = new ArrayList<>();
        JsonNode quantities = specification == null ? null : specification.path(QUANTITIES);
        for (JsonNode field : definition.path(REQUIRED_QUANTITIES)) {
            JsonNode matched = findQuantity(quantities, names(field));
            boolean invalid = matched == null || !matched.path(NORMALIZED_VALUE).isNumber()
                    || invalidNumericValue(field, matched.path(NORMALIZED_VALUE).asDouble())
                    || (!textSet(field.path(ALLOWED_UNITS)).isEmpty()
                        && !textSet(field.path(ALLOWED_UNITS)).contains(matched.path(NORMALIZED_UNIT).asText()))
                    || !sameUnitAsReference(quantities, field, matched);
            if (invalid) gaps.add(new RequiredGap(field.path("key").asText(),
                    field.path(ALLOWED_UNITS).isArray() && !field.path(ALLOWED_UNITS).isEmpty()
                            ? field.path(ALLOWED_UNITS).get(0).asText() : "SI"));
        }
        return List.copyOf(gaps);
    }

    /**
     * Resolve a user/AI quantity name once at the schema boundary. Numerical
     * solvers must never use this method or carry presentation aliases.
     */
    public String canonicalQuantityKey(JsonNode definition, String rawName) {
        String raw = rawName == null ? "" : rawName.trim();
        if (raw.isBlank()) return "";
        if (definition == null || !definition.isObject()) return "";
        for (JsonNode fields : List.of(definition.path(REQUIRED_QUANTITIES), definition.path("optionalQuantities"))) {
            for (JsonNode field : fields) {
                String key = field.path("key").asText("").trim();
                if (key.equals(raw)) return key;
                for (JsonNode alias : field.path("aliases")) if (alias.asText("").trim().equals(raw)) return key;
                JsonNode symbol = field.get("symbol");
                if (symbol != null && symbol.isTextual() && symbol.asText().trim().equals(raw)) return key;
                for (JsonNode item : field.path("symbols")) if (item.isTextual() && item.asText().trim().equals(raw)) return key;
                for (JsonNode parameter : definition.path(ADJUSTABLE_PARAMETERS)) {
                    if (key.equals(parameter.path("key").asText())
                            && parameter.path("symbol").isTextual()
                            && parameter.path("symbol").asText().trim().equals(raw)) return key;
                }
            }
        }
        Set<String> caseInsensitiveMatches = new HashSet<>();
        for (JsonNode fields : List.of(definition.path(REQUIRED_QUANTITIES), definition.path("optionalQuantities"))) {
            for (JsonNode field : fields) {
                String key = field.path("key").asText("").trim();
                if (key.equalsIgnoreCase(raw)) caseInsensitiveMatches.add(key);
                for (JsonNode alias : field.path("aliases")) {
                    if (alias.asText("").trim().equalsIgnoreCase(raw)) caseInsensitiveMatches.add(key);
                }
                JsonNode symbol = field.get("symbol");
                if (symbol != null && symbol.isTextual() && symbol.asText().trim().equalsIgnoreCase(raw)) {
                    caseInsensitiveMatches.add(key);
                }
                for (JsonNode item : field.path("symbols")) {
                    if (item.isTextual() && item.asText().trim().equalsIgnoreCase(raw)) caseInsensitiveMatches.add(key);
                }
                for (JsonNode parameter : definition.path(ADJUSTABLE_PARAMETERS)) {
                    if (key.equals(parameter.path("key").asText()) && parameter.path("symbol").isTextual()
                            && parameter.path("symbol").asText().trim().equalsIgnoreCase(raw)) {
                        caseInsensitiveMatches.add(key);
                    }
                }
            }
        }
        return caseInsensitiveMatches.size() == 1 ? caseInsensitiveMatches.iterator().next() : "";
    }

    /** Return a deep-copied quantity array whose names are schema keys. */
    public JsonNode canonicalizeQuantities(JsonNode quantities, JsonNode definition) {
        if (quantities == null || !quantities.isArray()) return quantities == null
                ? JsonNodeFactory.instance.arrayNode() : quantities.deepCopy();
        ArrayNode canonical = JsonNodeFactory.instance.arrayNode();
        Set<String> seen = new HashSet<>();
        for (JsonNode quantity : quantities) {
            if (!quantity.isObject()) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Quantity entries must be objects");
            ObjectNode copy = (ObjectNode) quantity.deepCopy();
            String key = canonicalQuantityKey(definition, quantity.path("name").asText());
            if (!StringUtils.hasText(key)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Unknown physical quantity: " + quantity.path("name").asText());
            if (!seen.add(key.toLowerCase(java.util.Locale.ROOT))) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Duplicate canonical physical quantity: " + key);
            copy.put("name", key);
            canonical.add(copy);
        }
        return canonical;
    }

    /** Materialize only defaults owned by the selected schema at the ingress boundary. */
    public JsonNode materializeDefaults(JsonNode specification, JsonNode definition) {
        ObjectNode copy = specification == null || !specification.isObject()
                ? JsonNodeFactory.instance.objectNode() : (ObjectNode) specification.deepCopy();
        ArrayNode quantities = copy.path(QUANTITIES).isArray()
                ? (ArrayNode) copy.path(QUANTITIES).deepCopy() : JsonNodeFactory.instance.arrayNode();
        Set<String> present = new HashSet<>();
        for (JsonNode quantity : quantities) present.add(quantity.path("name").asText());
        for (JsonNode field : definition.path("optionalQuantities")) {
            String key = field.path("key").asText("").trim();
            JsonNode value = field.get("defaultValue");
            if (key.isBlank() || value == null || !value.isNumber() || present.contains(key)) continue;
            ObjectNode materialized = JsonNodeFactory.instance.objectNode();
            materialized.put("name", key);
            materialized.set("value", value.deepCopy());
            materialized.set(NORMALIZED_VALUE, value.deepCopy());
            String unit = field.path(ALLOWED_UNITS).path(0).asText("1");
            materialized.put("originalUnit", unit);
            materialized.put(NORMALIZED_UNIT, unit);
            materialized.put("confidence", 1.0);
            materialized.put("sourceText", "schema.default");
            quantities.add(materialized);
            present.add(key);
        }
        copy.set(QUANTITIES, quantities);
        return copy;
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
            if (value == null) {
                JsonNode parameterDefinition = findQuantityDefinition(definition, control.key());
                if (parameterDefinition != null && parameterDefinition.path("defaultValue").isNumber()) {
                    value = parameterDefinition.path("defaultValue").asDouble();
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
        validateDefinitionInternal(definition, schemaId, null, null, true);
    }

    public void validateDefinition(JsonNode definition, String schemaId, String version, String topic) {
        validateDefinitionInternal(definition, schemaId, version, topic, false);
    }

    private void validateDefinitionInternal(JsonNode definition, String schemaId, String version, String topic,
                                            boolean legacyInferredIdentity) {
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
        validateValidationContract(validation, schemaId);
        validateQuantityDefinitions(definition, schemaId);
        validateVisualization(definition.path("visualization"), schemaId);
        try {
            if (legacyInferredIdentity) schemaCompiler.compile(definition, schemaId);
            else schemaCompiler.compile(definition, schemaId, version, topic);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        }
    }

    private void validateValidationContract(JsonNode validation, String schemaId) {
        JsonNode fractions = validation.path("checkpointFractions");
        if (!fractions.isArray() || fractions.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Validation checkpoints are required: " + schemaId);
        }
        double previous = 0;
        for (JsonNode fraction : fractions) {
            if (!fraction.isNumber() || !finite(fraction) || fraction.asDouble() <= 0
                    || fraction.asDouble() > 1 || fraction.asDouble() <= previous) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Validation checkpoint fractions must be finite, increasing and in (0,1]: " + schemaId);
            }
            previous = fraction.asDouble();
        }
        JsonNode outputs = validation.get("outputs");
        if (outputs == null || outputs.isNull()) return;
        if (!outputs.isObject() && !outputs.isArray()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Validation output tolerances must be an object or array: " + schemaId);
        }
        Iterable<JsonNode> definitions = outputs.isObject()
                ? java.util.stream.StreamSupport.stream(java.util.Spliterators.spliteratorUnknownSize(
                        outputs.fields(), 0), false).map(java.util.Map.Entry::getValue).toList()
                : outputs;
        Set<String> keys = new HashSet<>();
        for (JsonNode output : definitions) {
            String key = output.path("key").asText("").trim();
            if (outputs.isObject()) key = key.isBlank() ? "object-entry" : key;
            if (!outputs.isObject() && (key.isBlank() || !keys.add(key))) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Validation output key is missing or duplicated: " + schemaId);
            }
            double absolute = output.path("absoluteTolerance").asDouble(-1);
            double relative = output.path("relativeTolerance").asDouble(-1);
            String comparison = output.path("comparison").asText("numeric");
            if (!Double.isFinite(absolute) || absolute < 0 || !Double.isFinite(relative) || relative < 0
                    || !List.of("numeric", "exact", "discrete").contains(comparison)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Invalid output tolerance contract: " + schemaId + "." + key);
            }
        }
    }

    private void validateQuantityDefinitions(JsonNode definition, String schemaId) {
        Set<String> keys = new HashSet<>();
        Set<String> aliases = new HashSet<>();
        for (JsonNode fields : List.of(definition.path(REQUIRED_QUANTITIES), definition.path("optionalQuantities"))) {
            if (!fields.isMissingNode() && !fields.isArray()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Quantity definitions must be arrays: " + schemaId);
            }
            for (JsonNode field : fields) {
                String key = field.path("key").asText("").trim();
                if (key.isBlank() || !keys.add(key.toLowerCase(java.util.Locale.ROOT))) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Duplicate or missing quantity key in: " + schemaId);
                }
                JsonNode allowedUnits = field.path(ALLOWED_UNITS);
                if (!allowedUnits.isArray() || allowedUnits.isEmpty()) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Quantity allowedUnits are required for " + schemaId + "." + key);
                }
                Set<String> localAliases = new HashSet<>();
                for (JsonNode alias : field.path("aliases")) {
                    String normalized = alias.asText("").trim();
                    if (normalized.isBlank() || !localAliases.add(normalized)
                            || !aliases.add(normalized) || normalized.equals(key)) {
                        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                                "Duplicate or conflicting quantity alias in: " + schemaId + "." + key);
                    }
                }
                if (field.path("positive").asBoolean(false) && field.path("nonNegative").asBoolean(false)) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Quantity cannot be both positive and nonNegative: " + schemaId + "." + key);
                }
                JsonNode defaultValue = field.get("defaultValue");
                if (defaultValue != null && (!defaultValue.isNumber() || !finite(defaultValue))) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Quantity defaultValue must be finite: " + schemaId + "." + key);
                }
                if (defaultValue != null && defaultValue.isNumber()
                        && invalidNumericValue(field, defaultValue.asDouble())) {
                    throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                            "Quantity defaultValue violates constraints: " + schemaId + "." + key);
                }
            }
        }
        for (String key : keys) {
            if (aliases.contains(key)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Quantity alias conflicts with canonical key in: " + schemaId + "." + key);
            }
        }
        Set<String> controls = new HashSet<>();
        for (JsonNode control : definition.path(ADJUSTABLE_PARAMETERS)) {
            String key = control.path("key").asText("").trim();
            if (key.isBlank() || !controls.add(key.toLowerCase(java.util.Locale.ROOT)) || !keys.contains(key.toLowerCase(java.util.Locale.ROOT))) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Adjustable parameter is missing, duplicated or not a quantity: " + schemaId + "." + key);
            }
            if (!control.path("min").isNumber() || !control.path("max").isNumber()
                    || !control.path("step").isNumber() || !finite(control.path("min"))
                    || !finite(control.path("max")) || !finite(control.path("step"))
                    || control.path("min").asDouble() > control.path("max").asDouble()
                    || control.path("step").asDouble() <= 0) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Invalid adjustable parameter bounds: " + schemaId + "." + key);
            }
        }
    }

    private void validateVisualization(JsonNode visualization, String schemaId) {
        JsonNode series = visualization.path("series");
        JsonNode presentation = visualization.path("presentation");
        JsonNode nodes = presentation.path("sceneGraph").path("nodes");
        JsonNode actors = presentation.path("actors");
        if (!StringUtils.hasText(visualization.path("scene").asText()) || !series.isArray()) {
            invalidScene(schemaId, "scene and series are required");
        }
        if ((!nodes.isArray() || nodes.isEmpty()) && series.isEmpty() && (!actors.isArray() || actors.isEmpty())) {
            invalidScene(schemaId, "no sceneGraph, series, or actors to render");
        }
        Set<String> seriesKeys = new HashSet<>();
        Set<String> seriesSources = new HashSet<>();
        for (JsonNode item : series) {
            String key = item.path("key").asText("").trim();
            if (!StringUtils.hasText(key) || !seriesKeys.add(key)
                    || !StringUtils.hasText(item.path("source").asText())) invalidScene(schemaId, "invalid visualization series");
            seriesSources.add(item.path("source").asText());
        }
        if (nodes.isArray()) validateSceneNodes(nodes, schemaId, new HashSet<>(), seriesSources, 0, new int[] { 0 });
        for (JsonNode effect : presentation.path("effects")) {
            if (!SCENE_EFFECTS.contains(effect.asText())) invalidScene(schemaId, "unsupported effect: " + effect.asText());
        }
    }

    private void validateSceneNodes(JsonNode nodes, String schemaId, Set<String> ids, Set<String> seriesSources, int depth, int[] count) {
        if (!nodes.isArray() || depth > 32) invalidScene(schemaId, "invalid scene node nesting");
        for (JsonNode node : nodes) {
            if (++count[0] > 2_000) invalidScene(schemaId, "scene node resource limit exceeded");
            String id = node.path("id").asText("").trim();
            String type = node.path("type").asText("").trim();
            if (!StringUtils.hasText(id) || !ids.add(id)) invalidScene(schemaId, "missing or duplicate scene node id: " + id);
            if (!SCENE_PRIMITIVES.contains(type)) invalidScene(schemaId, "unsupported primitive: " + type);
            for (JsonNode binding : node.path("transform")) validateVisualBinding(binding, schemaId, seriesSources, 0);
            if (("graph".equals(type) || "chart".equals(type))
                    && !seriesSources.contains(node.path("properties").path("source").asText())) {
                invalidScene(schemaId, "graph source is not declared by visualization.series");
            }
            if ("vectorScene".equals(type)) validateVectorScene(node.path("properties").path("vector"), schemaId, seriesSources);
            if (node.has("children")) validateSceneNodes(node.path("children"), schemaId, ids, seriesSources, depth + 1, count);
        }
    }

    private void validateVectorScene(JsonNode vector, String schemaId, Set<String> seriesSources) {
        JsonNode viewBox = vector.path("viewBox");
        if (!viewBox.isArray() || viewBox.size() != 4 || !finite(viewBox.get(0)) || !finite(viewBox.get(1))
                || !finite(viewBox.get(2)) || !finite(viewBox.get(3)) || viewBox.get(2).asDouble() <= 0
                || viewBox.get(3).asDouble() <= 0) invalidScene(schemaId, "invalid vectorScene viewBox");
        validateVectorShapes(vector.path("shapes"), schemaId, seriesSources, 0, new int[] { 0, 0 });
    }

    private void validateVectorShapes(JsonNode shapes, String schemaId, Set<String> seriesSources, int depth, int[] count) {
        if (!shapes.isArray() || depth > 32) invalidScene(schemaId, "invalid vector shape nesting");
        for (JsonNode shape : shapes) {
            if (++count[0] > 2_000) invalidScene(schemaId, "vector shape resource limit exceeded");
            String kind = shape.path("kind").asText();
            if (!VECTOR_SHAPES.contains(kind)) invalidScene(schemaId, "unsupported vector shape: " + kind);
            for (String field : List.of("x", "y", "rotation", "scaleX", "scaleY", "width", "height", "radiusX",
                    "radiusY", "opacity", "lineWidth", "fontSize", "value")) {
                if (shape.has(field)) validateVisualBinding(shape.get(field), schemaId, seriesSources, 0);
            }
            if ("path".equals(kind)) {
                JsonNode commands = shape.path("commands");
                if (!commands.isArray() || commands.size() > 4_096) invalidScene(schemaId, "invalid vector path");
                count[1] += commands.size();
                if (count[1] > 20_000) invalidScene(schemaId, "vector path resource limit exceeded");
                for (JsonNode command : commands) {
                    Integer arity = PATH_ARITY.get(command.path("op").asText());
                    JsonNode args = command.path("args");
                    if (arity == null || !args.isArray() || args.size() != arity) invalidScene(schemaId, "invalid path command");
                    args.forEach(arg -> validateVisualBinding(arg, schemaId, seriesSources, 0));
                }
            }
            if (shape.has("children")) validateVectorShapes(shape.path("children"), schemaId, seriesSources, depth + 1, count);
        }
    }

    private void validateVisualBinding(JsonNode binding, String schemaId, Set<String> seriesSources, int depth) {
        if (depth > 32) invalidScene(schemaId, "visual binding nesting exceeds 32");
        if (binding.isNumber()) { if (!finite(binding)) invalidScene(schemaId, "visual literal must be finite"); return; }
        if (binding.isTextual()) {
            if (!StringUtils.hasText(binding.asText()) || !seriesSources.contains(binding.asText()))
                invalidScene(schemaId, "undeclared series binding: " + binding.asText());
            return;
        }
        if (!binding.isObject()) invalidScene(schemaId, "invalid visual binding");
        String source = binding.path("source").asText();
        if ("constant".equals(source)) { if (!finite(binding.path("value"))) invalidScene(schemaId, "invalid constant binding"); return; }
        if ("series".equals(source) || "quantity".equals(source)) {
            if (!StringUtils.hasText(binding.path("key").asText())) invalidScene(schemaId, "binding key is required");
            return;
        }
        if ("entity".equals(source)) {
            if (!StringUtils.hasText(binding.path("entityId").asText()) || !StringUtils.hasText(binding.path("path").asText()))
                invalidScene(schemaId, "entity binding requires entityId/path");
            return;
        }
        if (!"expression".equals(source)) invalidScene(schemaId, "unsupported binding source: " + source);
        String operator = binding.path("operator").asText();
        JsonNode args = binding.path("args");
        int expected = "clamp".equals(operator) ? 3 : Set.of("abs", "negate", "sin", "cos").contains(operator) ? 1 : -1;
        if (!EXPRESSION_OPERATORS.contains(operator) || !args.isArray() || (expected >= 0 && args.size() != expected)
                || (expected < 0 && args.size() < 2)) invalidScene(schemaId, "invalid visual expression");
        args.forEach(arg -> validateVisualBinding(arg, schemaId, seriesSources, depth + 1));
    }

    private boolean finite(JsonNode value) {
        return value != null && value.isNumber() && Double.isFinite(value.asDouble());
    }

    private void invalidScene(String schemaId, String detail) {
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid visualization for " + schemaId + ": " + detail);
    }

    private JsonNode findQuantity(JsonNode quantities, Set<String> acceptedNames) {
        if (quantities == null || !quantities.isArray()) return null;
        for (JsonNode quantity : quantities) {
            String name = quantity.path("name").asText("").toLowerCase();
            if (acceptedNames.contains(name)) return quantity;
        }
        return null;
    }

    private JsonNode findQuantityForControl(JsonNode specification, JsonNode definition, String key) {
        JsonNode field = findQuantityDefinition(definition, key);
        Set<String> acceptedNames = field == null ? Set.of(key.toLowerCase()) : names(field);
        return findQuantity(specification == null ? null : specification.path(QUANTITIES), acceptedNames);
    }

    private JsonNode findQuantityDefinition(JsonNode definition, String key) {
        for (JsonNode fields : List.of(definition.path(REQUIRED_QUANTITIES), definition.path("optionalQuantities"))) {
            for (JsonNode field : fields) {
                if (key.equalsIgnoreCase(field.path("key").asText())) return field;
            }
        }
        return null;
    }

    private Set<String> names(JsonNode field) {
        Set<String> names = new HashSet<>();
        names.add(field.path("key").asText().toLowerCase());
        return names;
    }

    private Set<String> textSet(JsonNode values) {
        Set<String> result = new HashSet<>();
        if (values.isArray()) values.forEach(value -> result.add(value.asText()));
        return result;
    }

    private boolean invalidNumericValue(JsonNode field, double value) {
        return !Double.isFinite(value)
                || (field.path("positive").asBoolean(false) && value <= 0)
                || (field.path("nonNegative").asBoolean(false) && value < 0)
                || (field.path("integer").asBoolean(false) && Math.rint(value) != value);
    }

    private boolean sameUnitAsReference(JsonNode quantities, JsonNode field, JsonNode matched) {
        String reference = field.path("sameUnitAs").asText("").trim();
        if (reference.isBlank() || matched == null) return true;
        JsonNode referenceQuantity = findQuantity(quantities, Set.of(reference.toLowerCase()));
        return referenceQuantity == null
                || matched.path(NORMALIZED_UNIT).asText().equals(referenceQuantity.path(NORMALIZED_UNIT).asText());
    }
}
