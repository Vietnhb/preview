package com.example.backend.service.problem;

import com.example.backend.entity.curriculum.Topic;
import com.example.backend.repository.curriculum.TopicRepository;

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

import com.example.backend.entity.enums.LifecycleStatus;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.entity.simulation.SolverVersion;
import com.example.backend.exception.ApiException;
import com.example.backend.physics.validation.EndConditionResolver;
import com.example.backend.physics.model.PhysicsValues;
import com.example.backend.repository.problem.SchemaVersionRepository;
import com.example.backend.repository.simulation.SolverVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

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

    @Transactional(readOnly = true)
    public SchemaVersion requireApproved(String schemaId) {
        if (!StringUtils.hasText(schemaId)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Schema is missing");
        String canonicalSchemaId = PhysicsValues.canonicalName(schemaId);
        SchemaVersion schema = repository.findByLifecycleStatus(LifecycleStatus.APPROVED).stream()
                .filter(item -> item.getSchemaId().equalsIgnoreCase(canonicalSchemaId))
                .max(Comparator.comparing(SchemaVersion::getCreatedAt))
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No approved schema definition exists for: " + canonicalSchemaId));
        validateDefinition(schema.getDefinition(), canonicalSchemaId);
        requireEnabledTopic(schema.getTopic());
        return schema;
    }

    @Transactional(readOnly = true)
    public SchemaVersion requireApproved(String schemaId, String version) {
        if (!StringUtils.hasText(schemaId) || !StringUtils.hasText(version)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Persisted schema binding is missing");
        }
        String canonicalSchemaId = PhysicsValues.canonicalName(schemaId);
        SchemaVersion schema = repository.findFirstBySchemaIdAndVersion(canonicalSchemaId, version)
                .filter(item -> item.getLifecycleStatus() == LifecycleStatus.APPROVED)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "Persisted schema binding is not approved: " + canonicalSchemaId + "@" + version));
        validateDefinition(schema.getDefinition(), canonicalSchemaId);
        requireEnabledTopic(schema.getTopic());
        return schema;
    }

    @Transactional(readOnly = true)
    public List<SchemaVersion> approvedSchemas() {
        var enabledTopics = topicRepository.findAll().stream().filter(com.example.backend.entity.curriculum.Topic::isEnabled)
                .map(t -> t.getName().toLowerCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        return new java.util.TreeMap<>(repository.findByLifecycleStatus(LifecycleStatus.APPROVED).stream()
                .filter(s -> enabledTopics.contains(s.getTopic().toLowerCase(java.util.Locale.ROOT)))
                .collect(java.util.stream.Collectors.toMap(item -> item.getSchemaId().toLowerCase(), item -> item,
                        (left, right) -> left.getCreatedAt().compareTo(right.getCreatedAt()) >= 0 ? left : right)))
                .values().stream().toList();
    }

    /** Historical rendering is read-only and must survive retirement of a version. */
    @Transactional(readOnly = true)
    public SchemaVersion requireHistorical(String schemaId, String version) {
        String canonicalSchemaId = PhysicsValues.canonicalName(schemaId);
        return repository.findFirstBySchemaIdAndVersion(canonicalSchemaId, version)
                .filter(item -> item.getLifecycleStatus() != LifecycleStatus.DRAFT)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Historical schema version not found"));
    }

    private void requireEnabledTopic(String topic) {
        if (topicRepository.findAll().stream().noneMatch(t -> t.isEnabled() && t.getName().equalsIgnoreCase(topic)))
            throw new ApiException(HttpStatus.CONFLICT, "Curriculum topic is disabled: " + topic);
    }

    @Transactional(readOnly = true)
    public SolverBinding requireSolverBinding(String schemaId) {
        String canonicalSchemaId = PhysicsValues.canonicalName(schemaId);
        SolverVersion solver = solverRepository.findFirstBySchemaIdAndLifecycleStatusOrderByCreatedAtDesc(
                canonicalSchemaId, LifecycleStatus.APPROVED).orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No approved solver binding exists for: " + canonicalSchemaId));
        String referenceId = solver.getOutputDefinition().path("referenceSolverId").asText();
        if (!StringUtils.hasText(solver.getSolverId()) || !StringUtils.hasText(referenceId)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Approved solver binding is incomplete: " + canonicalSchemaId);
        }
        return new SolverBinding(solver.getSolverId(), referenceId, solver.getVersion());
    }

    @Transactional(readOnly = true)
    public SolverBinding requireSolverBinding(String schemaId, String version) {
        String canonicalSchemaId = PhysicsValues.canonicalName(schemaId);
        SolverVersion solver = solverRepository.findFirstBySchemaIdAndVersion(canonicalSchemaId, version)
                .filter(item -> item.getLifecycleStatus() == LifecycleStatus.APPROVED)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "Persisted solver binding is not approved: " + canonicalSchemaId + "@" + version));
        String referenceId = solver.getOutputDefinition().path("referenceSolverId").asText();
        if (!StringUtils.hasText(solver.getSolverId()) || !StringUtils.hasText(referenceId)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Persisted solver binding is incomplete: " + canonicalSchemaId + "@" + version);
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
        return List.copyOf(blockers);
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
        if (definition == null || !definition.isObject()) return raw;
        for (JsonNode fields : List.of(definition.path(REQUIRED_QUANTITIES), definition.path("optionalQuantities"))) {
            for (JsonNode field : fields) {
                String key = field.path("key").asText("").trim();
                if (key.equalsIgnoreCase(raw)) return key;
                for (JsonNode alias : field.path("aliases")) {
                    if (alias.asText("").trim().equalsIgnoreCase(raw)) return key;
                }
            }
        }
        return raw;
    }

    /** Return a deep-copied quantity array whose names are schema keys. */
    public JsonNode canonicalizeQuantities(JsonNode quantities, JsonNode definition) {
        if (quantities == null || !quantities.isArray()) return quantities == null
                ? JsonNodeFactory.instance.arrayNode() : quantities.deepCopy();
        ArrayNode canonical = JsonNodeFactory.instance.arrayNode();
        for (JsonNode quantity : quantities) {
            if (!quantity.isObject()) {
                canonical.add(quantity.deepCopy());
                continue;
            }
            ObjectNode copy = (ObjectNode) quantity.deepCopy();
            copy.put("name", canonicalQuantityKey(definition, quantity.path("name").asText()));
            canonical.add(copy);
        }
        return canonical;
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
        validateVisualization(definition.path("visualization"), schemaId);
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
            String symbol = quantity.path("symbol").asText("").toLowerCase();
            if (acceptedNames.contains(name) || acceptedNames.contains(symbol)) return quantity;
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
        for (JsonNode alias : field.path("aliases")) names.add(alias.asText().toLowerCase());
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
