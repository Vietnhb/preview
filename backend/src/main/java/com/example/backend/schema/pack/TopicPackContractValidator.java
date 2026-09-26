package com.example.backend.schema.pack;

import com.example.backend.ai.extraction.validation.ResponseSchemaValidator;
import com.example.backend.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Checks the versioned, data-only shape and internal references of topic packs. */
@Component
public final class TopicPackContractValidator {
    public static final String CURRENT_META_SCHEMA_VERSION = "2.0";
    private static final String META_SCHEMA_VERSION = "metaSchemaVersion";
    private static final String OBJECT_TYPES = "objectTypes";
    private static final String RELATION_TYPES = "relationTypes";
    private static final String QUANTITY_DEFINITIONS = "quantityDefinitions";
    private static final String REQUIRED_QUANTITIES = "requiredQuantities";
    private static final String OPTIONAL_QUANTITIES = "optionalQuantities";
    private static final String QUANTITIES = "quantities";
    private static final String OBJECTS = "objects";
    private static final String APPLIES_TO = "appliesTo";
    private static final String SYMBOL = "symbol";
    private static final String ALIASES = "aliases";
    private static final String ALLOWED_UNITS = "allowedUnits";
    private static final String TYPES = "types";
    private static final String SUBJECT = "subject";
    private static final String OBJECT = "object";
    private static final String ENTITY_CONTRACT = "entityContract";
    private static final String ENTITY_TYPES = "entityTypes";
    private final JsonNode metaSchema;
    private final JsonNode coreTypeLibrary;

    public TopicPackContractValidator(ObjectMapper mapper, ResourceLoader resources) {
        try (var schema = resources.getResource("classpath:schemas/topic-pack.meta-schema-2.0.json").getInputStream();
                var core = resources.getResource("classpath:schemas/core-types/registry.json").getInputStream()) {
            this.metaSchema = mapper.readTree(schema);
            this.coreTypeLibrary = mapper.readTree(core);
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot load the topic-pack contract resources", failure);
        }
    }

    public JsonNode metaSchema() { return metaSchema.deepCopy(); }
    public JsonNode coreTypeLibrary() { return coreTypeLibrary.deepCopy(); }

    public JsonNode projectCoreTypes(JsonNode definition) {
        if (definition == null || !definition.isObject()) return definition;
        ObjectNode copy = (ObjectNode) definition.deepCopy();
        var projected = copy.putArray("coreTypeDefinitions");
        Set<String> requested = strings(definition.path("coreTypeRefs"));
        for (JsonNode type : coreTypeLibrary.path(TYPES))
            if (requested.contains(type.path("id").asText())) projected.add(type.deepCopy());
        return copy;
    }

    /** New drafts must opt into the current data-only topic template contract. */
    public void validateForAuthoring(JsonNode definition, String schemaId) {
        String version = definition == null ? "" : definition.path(META_SCHEMA_VERSION).asText("");
        if (version.isBlank()) {
            reject("metaSchemaVersion is required for a new topic pack");
        }
        validateIfDeclared(definition, schemaId);
    }

    /** Validates a declared current topic template without interpreting its domain. */
    public void validateIfDeclared(JsonNode definition, String schemaId) {
        if (definition == null || !definition.has(META_SCHEMA_VERSION)) return;
        String version = definition.path(META_SCHEMA_VERSION).asText("");
        if (!CURRENT_META_SCHEMA_VERSION.equals(version)) {
            reject("Unsupported topic-pack meta-schema version for " + schemaId + ": " + version);
        }
        try {
            ResponseSchemaValidator.validate(definition, metaSchema);
        } catch (IllegalArgumentException invalid) {
            reject("Topic pack " + schemaId + " does not match meta-schema " + version + ": " + invalid.getMessage());
        }
        validateReferences(definition, schemaId);
        validateConceptCatalog(definition, schemaId);
    }

    /** Referential checks only; this does not infer values or interpret domain rules. */
    public void validateSpecificationReferences(JsonNode specification, JsonNode definition, String schemaId) {
        Set<String> objectTypes = objectTypes(definition);
        Set<String> relationTypes = relationTypes(definition);
        Set<String> rootQuantities = quantityNames(definition.path(REQUIRED_QUANTITIES));
        rootQuantities.addAll(quantityNames(definition.path(OPTIONAL_QUANTITIES)));
        boolean conceptPack = CURRENT_META_SCHEMA_VERSION.equals(definition.path(META_SCHEMA_VERSION).asText());
        boolean openWorld = conceptPack && definition.path("conceptVocabulary").path("openWorld").asBoolean(false);
        if (!conceptPack) rootQuantities.addAll(quantityNames(definition.path(QUANTITY_DEFINITIONS)));
        boolean versionedPack = definition.has(META_SCHEMA_VERSION);
        if (!openWorld && !rootQuantities.isEmpty()) validateQuantityReferences(specification.path(QUANTITIES), rootQuantities, "root", schemaId);
        else if (!openWorld && versionedPack && !specification.path(QUANTITIES).isEmpty())
            reject("Specification has root quantities, but " + schemaId + " declares none");
        Set<String> objects = new HashSet<>();
        if (!openWorld && versionedPack && objectTypes.isEmpty() && specification.path(OBJECTS).isArray()
                && !specification.path(OBJECTS).isEmpty())
            reject("Specification declares objects, but " + schemaId + " declares no object types");
        for (JsonNode object : specification.path(OBJECTS)) {
            String id = object.path("id").asText("");
            String type = object.path("type").asText("");
            if (id.isBlank() || !objects.add(id)) reject("Specification contains a missing or duplicate object id");
            if (!openWorld && (versionedPack ? !objectTypes.contains(type) : !objectTypes.isEmpty() && !objectTypes.contains(type)))
                reject("Specification object type is not declared by " + schemaId + ": " + type);
            JsonNode declaredType = findObjectType(definition, type);
            if (declaredType != null) {
                Set<String> localQuantities = quantityNames(declaredType.path(REQUIRED_QUANTITIES));
                localQuantities.addAll(quantityNames(declaredType.path(OPTIONAL_QUANTITIES)));
                if (conceptPack) {
                    for (JsonNode quantity : definition.path(QUANTITY_DEFINITIONS)) {
                        JsonNode applicable = quantity.path(APPLIES_TO).path(OBJECT_TYPES);
                        if (strings(applicable).contains(type)) localQuantities.addAll(quantityNamesForOne(quantity));
                    }
                }
                if (!openWorld && (conceptPack || !localQuantities.isEmpty())) validateQuantityReferences(object.path(QUANTITIES),
                        localQuantities, "object " + id, schemaId);
            }
        }
        for (JsonNode relation : specification.path("relations")) {
            String type = relation.path("type").asText("");
            if (!openWorld && relationTypes.isEmpty() && versionedPack)
                reject("Specification declares a relation, but " + schemaId + " declares no relation types");
            if (!openWorld && !relationTypes.isEmpty() && !relationTypes.contains(type))
                reject("Specification relation type is not declared by " + schemaId + ": " + type);
            requireObjectReference(relation.path(SUBJECT).asText(""), objects, SUBJECT);
            if (relation.hasNonNull(OBJECT)) requireObjectReference(relation.path(OBJECT).asText(""), objects, OBJECT);
            if (conceptPack) validateRelationRoles(relation, definition, specification.path(OBJECTS), schemaId);
        }
        Set<String> endConditions = strings(definition.path("endConditionCapabilities"));
        String endType = specification.path("endCondition").path("type").asText("");
        if (!endType.isBlank() && ((versionedPack && definition.has("endConditionCapabilities"))
                ? !endConditions.contains(endType)
                : !endConditions.isEmpty() && !endConditions.contains(endType)))
            reject("Specification end condition is not declared by " + schemaId + ": " + endType);
    }

    private void validateConceptCatalog(JsonNode definition, String schemaId) {
        unique(definition.path(OBJECT_TYPES), "type", "object type", schemaId);
        unique(definition.path(RELATION_TYPES), "type", "relation type", schemaId);
        unique(definition.path(QUANTITY_DEFINITIONS), "key", "quantity", schemaId);
        unique(definition.path("unitCatalog"), SYMBOL, "unit", schemaId);
        unique(definition.path("laws"), "id", "law", schemaId);
        unique(definition.path("applicationRequirements"), "id", "application requirement", schemaId);
        for (JsonNode quantity : definition.path(QUANTITY_DEFINITIONS)) {
            if (quantity.has("defaultValue")) reject("Concept quantity cannot supply a user value in " + schemaId);
            if (quantity.path(APPLIES_TO).path(OBJECT_TYPES).isEmpty())
                reject("Concept quantity needs applicable object types in " + schemaId + ": " + quantity.path("key").asText());
        }
        Set<String> quantityKeys = quantityKeys(definition);
        for (JsonNode law : definition.path("laws")) {
            validateReferenceSet(law.path("inputs"), quantityKeys, "law input", schemaId);
            validateReferenceSet(law.path("outputs"), quantityKeys, "law output", schemaId);
        }
    }

    private void unique(JsonNode entries, String field, String label, String schemaId) {
        Set<String> seen = new HashSet<>();
        for (JsonNode entry : entries) {
            String value = entry.path(field).asText("").trim();
            if (value.isBlank() || !seen.add(value)) reject("Duplicate or missing " + label + " in " + schemaId + ": " + value);
        }
    }

    private void validateRelationRoles(JsonNode relation, JsonNode definition, JsonNode objects, String schemaId) {
        String relationType = relation.path("type").asText("");
        JsonNode declared = null;
        for (JsonNode candidate : definition.path(RELATION_TYPES)) {
            if (relationType.equals(candidate.path("type").asText())) { declared = candidate; break; }
        }
        if (declared == null) return;
        Map<String, String> typesById = new HashMap<>();
        for (JsonNode object : objects) typesById.put(object.path("id").asText(), object.path("type").asText());
        String subjectType = typesById.get(relation.path(SUBJECT).asText());
        Set<String> subjects = strings(declared.path("subjectTypes"));
        if (!subjects.isEmpty() && !subjects.contains(subjectType))
            reject("Relation subject type is invalid for " + schemaId + ": " + relationType);
        Set<String> targets = strings(declared.path(OBJECT_TYPES));
        String objectId = relation.path(OBJECT).asText("");
        if (!targets.isEmpty() && (objectId.isBlank() || !targets.contains(typesById.get(objectId))))
            reject("Relation object type is invalid for " + schemaId + ": " + relationType);
    }

    private Set<String> quantityNamesForOne(JsonNode quantity) {
        com.fasterxml.jackson.databind.node.ArrayNode one = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        one.add(quantity);
        return quantityNames(one);
    }

    private void validateReferences(JsonNode definition, String schemaId) {
        Set<String> objectTypes = objectTypes(definition);
        Set<String> relationTypes = relationTypes(definition);
        Set<String> quantityKeys = quantityKeys(definition);
        Set<String> coreTypes = new HashSet<>();
        for (JsonNode type : coreTypeLibrary.path(TYPES)) coreTypes.add(type.path("id").asText());
        for (JsonNode reference : definition.path("coreTypeRefs")) {
            if (!reference.isTextual() || !coreTypes.contains(reference.asText()))
                reject("Unknown core type reference in " + schemaId + ": " + reference.asText());
        }
        for (JsonNode relation : definition.path(RELATION_TYPES)) {
            for (String field : Set.of("subjectTypes", OBJECT_TYPES)) {
                for (JsonNode reference : relation.path(field)) {
                    if (!objectTypes.contains(reference.asText()))
                        reject("Unknown relation object type reference in " + schemaId + ": " + reference.asText());
                }
            }
            validateApplicability(relation.path(APPLIES_TO), objectTypes, relationTypes, quantityKeys, schemaId);
            quantityKeys.addAll(quantityNames(relation.path(REQUIRED_QUANTITIES)));
            quantityKeys.addAll(quantityNames(relation.path(OPTIONAL_QUANTITIES)));
        }
        JsonNode declaredObjects = definition.path(OBJECT_TYPES);
        if (!declaredObjects.isArray()) declaredObjects = definition.path(ENTITY_CONTRACT).path(TYPES);
        if (!declaredObjects.isArray()) declaredObjects = definition.path(ENTITY_TYPES);
        for (JsonNode objectType : declaredObjects) {
            validateApplicability(objectType.path(APPLIES_TO), objectTypes, relationTypes, quantityKeys, schemaId);
            validateQuantityApplicability(objectType.path(REQUIRED_QUANTITIES), objectTypes, relationTypes,
                    quantityKeys, schemaId);
            validateQuantityApplicability(objectType.path(OPTIONAL_QUANTITIES), objectTypes, relationTypes,
                    quantityKeys, schemaId);
        }
        validateQuantityApplicability(definition.path(REQUIRED_QUANTITIES), objectTypes, relationTypes,
                quantityKeys, schemaId);
        validateQuantityApplicability(definition.path(OPTIONAL_QUANTITIES), objectTypes, relationTypes,
                quantityKeys, schemaId);
        validateQuantityApplicability(definition.path(QUANTITY_DEFINITIONS), objectTypes, relationTypes,
                quantityKeys, schemaId);
        for (JsonNode law : definition.path("laws")) {
            validateApplicability(law.path(APPLIES_TO), objectTypes, relationTypes, quantityKeys, schemaId);
        }
        for (JsonNode requirement : definition.path("applicationRequirements")) {
            if (requirement.isObject()) validateApplicability(requirement.path(APPLIES_TO), objectTypes,
                    relationTypes, quantityKeys, schemaId);
        }
        validateUnitReferences(definition, schemaId);
    }

    private void validateQuantityApplicability(JsonNode quantities, Set<String> objectTypes,
            Set<String> relationTypes, Set<String> quantityKeys, String schemaId) {
        for (JsonNode quantity : quantities)
            validateApplicability(quantity.path(APPLIES_TO), objectTypes, relationTypes, quantityKeys, schemaId);
    }

    private void validateApplicability(JsonNode applicability, Set<String> objectTypes,
            Set<String> relationTypes, Set<String> quantityKeys, String schemaId) {
        validateReferenceSet(applicability.path(OBJECT_TYPES), objectTypes, "object type", schemaId);
        validateReferenceSet(applicability.path(RELATION_TYPES), relationTypes, "relation type", schemaId);
        validateReferenceSet(applicability.path(QUANTITIES), quantityKeys, "quantity", schemaId);
    }

    private void validateReferenceSet(JsonNode values, Set<String> known, String label, String schemaId) {
        for (JsonNode value : values) {
            if (!known.contains(value.asText()))
                reject("Unknown " + label + " reference in " + schemaId + ": " + value.asText());
        }
    }

    private Set<String> objectTypes(JsonNode definition) {
        JsonNode nodes = definition.path(OBJECT_TYPES);
        if (!nodes.isArray()) nodes = definition.path(ENTITY_CONTRACT).path(TYPES);
        if (!nodes.isArray()) nodes = definition.path(ENTITY_TYPES);
        Set<String> result = new HashSet<>();
        for (JsonNode node : nodes) {
            String value = node.path("type").asText("");
            if (!value.isBlank()) result.add(value);
        }
        return result;
    }

    private Set<String> relationTypes(JsonNode definition) {
        JsonNode nodes = definition.path(RELATION_TYPES);
        Set<String> result = new HashSet<>();
        for (JsonNode node : nodes) {
            String value = node.isTextual() ? node.asText() : node.path("type").asText("");
            if (!value.isBlank()) result.add(value);
        }
        if (result.isEmpty()) {
            JsonNode legacy = definition.path("relationContract").path(TYPES);
            for (JsonNode node : legacy) {
                String value = node.isTextual() ? node.asText() : node.path("type").asText("");
                if (!value.isBlank()) result.add(value);
            }
        }
        return result;
    }

    private Set<String> quantityKeys(JsonNode definition) {
        Set<String> result = quantityNames(definition.path(REQUIRED_QUANTITIES));
        result.addAll(quantityNames(definition.path(OPTIONAL_QUANTITIES)));
        result.addAll(quantityNames(definition.path(QUANTITY_DEFINITIONS)));
        JsonNode objectDefinitions = definition.path(OBJECT_TYPES);
        if (!objectDefinitions.isArray()) objectDefinitions = definition.path(ENTITY_CONTRACT).path(TYPES);
        if (!objectDefinitions.isArray()) objectDefinitions = definition.path(ENTITY_TYPES);
        for (JsonNode type : objectDefinitions) {
            result.addAll(quantityNames(type.path(REQUIRED_QUANTITIES)));
            result.addAll(quantityNames(type.path(OPTIONAL_QUANTITIES)));
        }
        for (JsonNode relation : definition.path(RELATION_TYPES)) {
            result.addAll(quantityNames(relation.path(REQUIRED_QUANTITIES)));
            result.addAll(quantityNames(relation.path(OPTIONAL_QUANTITIES)));
        }
        return result;
    }

    private void validateUnitReferences(JsonNode definition, String schemaId) {
        Set<String> units = new HashSet<>();
        for (JsonNode unit : definition.path("unitCatalog")) {
            String symbol = unit.path(SYMBOL).asText("");
            if (!symbol.isBlank()) units.add(symbol);
        }
        if (units.isEmpty()) return;
        java.util.List<JsonNode> declarations = new java.util.ArrayList<>();
        for (String key : java.util.List.of(REQUIRED_QUANTITIES, OPTIONAL_QUANTITIES, QUANTITY_DEFINITIONS))
            definition.path(key).forEach(declarations::add);
        JsonNode objectDefinitions = definition.path(OBJECT_TYPES);
        if (!objectDefinitions.isArray()) objectDefinitions = definition.path(ENTITY_CONTRACT).path(TYPES);
        if (!objectDefinitions.isArray()) objectDefinitions = definition.path(ENTITY_TYPES);
        for (JsonNode objectType : objectDefinitions) {
            objectType.path(REQUIRED_QUANTITIES).forEach(declarations::add);
            objectType.path(OPTIONAL_QUANTITIES).forEach(declarations::add);
        }
        for (JsonNode relation : definition.path(RELATION_TYPES)) {
            relation.path(REQUIRED_QUANTITIES).forEach(declarations::add);
            relation.path(OPTIONAL_QUANTITIES).forEach(declarations::add);
        }
        for (JsonNode quantity : declarations) {
            for (JsonNode unit : quantity.path(ALLOWED_UNITS)) {
                if (!units.contains(unit.asText()))
                    reject("Unknown unit reference in " + schemaId + ": " + unit.asText());
            }
        }
    }

    private Set<String> quantityNames(JsonNode values) {
        Set<String> destination = new HashSet<>();
        for (JsonNode value : values) {
            String key = value.path("key").asText("");
            if (!key.isBlank()) destination.add(key);
            for (JsonNode alias : value.path(ALIASES)) if (alias.isTextual()) destination.add(alias.asText());
            if (value.path(SYMBOL).isTextual()) destination.add(value.path(SYMBOL).asText());
            for (JsonNode symbol : value.path("symbols")) if (symbol.isTextual()) destination.add(symbol.asText());
        }
        return destination;
    }

    private JsonNode findObjectType(JsonNode definition, String type) {
        JsonNode values = definition.path(OBJECT_TYPES);
        if (!values.isArray()) values = definition.path(ENTITY_CONTRACT).path(TYPES);
        if (!values.isArray()) values = definition.path(ENTITY_TYPES);
        for (JsonNode value : values) if (type.equals(value.path("type").asText())) return value;
        return null;
    }

    private void validateQuantityReferences(JsonNode quantities, Set<String> allowed, String scope, String schemaId) {
        for (JsonNode quantity : quantities) {
            String name = quantity.path("name").asText("");
            if (!allowed.contains(name)) reject("Unknown quantity reference in " + scope + " for " + schemaId + ": " + name);
        }
    }

    private Set<String> strings(JsonNode values) {
        Set<String> result = new HashSet<>();
        for (JsonNode value : values) if (value.isTextual()) result.add(value.asText());
        return result;
    }

    private void requireObjectReference(String id, Set<String> objects, String field) {
        if (id.isBlank() || !objects.contains(id)) reject("Relation " + field + " does not reference a declared object: " + id);
    }

    private void reject(String message) {
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
