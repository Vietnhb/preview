package com.example.backend.system.physics.service;

import com.example.backend.base.crud.model.enums.LifecycleStatus;
import com.example.backend.exception.ApiException;
import com.example.backend.exception.SchemaCompilationException;
import com.example.backend.system.curriculum.model.entity.Topic;
import com.example.backend.system.curriculum.repository.TopicRepository;
import com.example.backend.system.physics.model.entity.SchemaVersion;
import com.example.backend.system.physics.repository.SchemaVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Access and integrity boundary for reviewer-managed topic definitions. */
@Service
public class SchemaDefinitionService {
    private final SchemaVersionRepository schemas;
    private final TopicRepository topics;
    private final ObjectMapper json;

    public SchemaDefinitionService(SchemaVersionRepository schemas, TopicRepository topics, ObjectMapper json) {
        this.schemas = schemas;
        this.topics = topics;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<SchemaVersion> approvedSchemas() {
        List<String> enabledTopics = topics.findByEnabledTrueOrderBySortOrderAsc().stream().map(Topic::getName).toList();
        if (enabledTopics.isEmpty()) return List.of();
        Map<String, SchemaVersion> latest = schemas
                .findAllByLifecycleStatusAndTopicInOrderByTopicAscSchemaIdAscCreatedAtDesc(
                        LifecycleStatus.APPROVED, enabledTopics).stream()
                .collect(java.util.stream.Collectors.toMap(s -> s.getSchemaId().toLowerCase(), s -> s,
                        SchemaVersionOrdering::newer));
        return new java.util.TreeMap<>(latest).values().stream().toList();
    }

    @Transactional(readOnly = true)
    public SchemaVersion requireApproved(String schemaId) {
        return findApproved(schemaId).orElseThrow(() -> ApiException.unprocessable("No approved schema definition exists for: " + schemaId));
    }

    @Transactional(readOnly = true)
    public SchemaVersion requireCurrentApproved(String schemaId, String version) {
        SchemaVersion schema = schemas.findFirstBySchemaIdAndVersion(schemaId, version)
                .filter(s -> s.getLifecycleStatus() == LifecycleStatus.APPROVED)
                .orElseThrow(() -> ApiException.conflict("Routed schema is no longer approved: " + schemaId + "@" + version));
        if (approvedSchemas().stream().noneMatch(s -> s.getSchemaId().equalsIgnoreCase(schemaId)
                && s.getVersion().equals(version))) {
            throw ApiException.conflict("Routed schema version is no longer current and enabled");
        }
        validateDefinition(schema.getDefinition(), schema.getSchemaId(), schema.getVersion(), schema.getTopic());
        return schema;
    }

    public void validateDefinition(JsonNode definition, String schemaId) {
        validateDefinition(definition, schemaId, "unspecified", "unspecified");
    }

    public void validateDefinition(JsonNode definition, String schemaId, String version, String topic) {
        if (!StringUtils.hasText(schemaId) || !StringUtils.hasText(version) || !StringUtils.hasText(topic)
                || definition == null || !definition.isObject()) {
            throw new SchemaCompilationException("Topic definition and schema identity are required");
        }
        JsonNode metaVersion = definition.path("metaSchemaVersion");
        if (metaVersion.isTextual() && !metaVersion.asText().isBlank()
                && !metaVersion.asText().matches("[0-9]+\\.[0-9]+")) {
            throw new SchemaCompilationException("Topic definition metaSchemaVersion must use MAJOR.MINOR format");
        }
    }

    public void validateTopicPackForAuthoring(JsonNode definition, String schemaId) {
        validateDefinition(definition, schemaId);
    }

    public JsonNode topicPackMetaSchema() { return readSchemaResource("schemas/topic-pack.meta-schema-2.0.json"); }
    public JsonNode coreTypeLibrary() { return readSchemaResource("schemas/core-types/registry.json"); }

    public String compiledChecksum(JsonNode definition) { return checksum(definition); }

    private Optional<SchemaVersion> findApproved(String schemaId) {
        if (!StringUtils.hasText(schemaId)) return Optional.empty();
        return schemas.findAllBySchemaIdIgnoreCaseAndLifecycleStatusOrderByCreatedAtDesc(
                schemaId.trim(), LifecycleStatus.APPROVED).stream().reduce(SchemaVersionOrdering::newer);
    }

    private JsonNode readSchemaResource(String path) {
        try { return json.readTree(new ClassPathResource(path).getInputStream()); }
        catch (Exception exception) { throw new IllegalStateException("Required schema resource is unavailable: " + path, exception); }
    }

    private String checksum(JsonNode definition) {
        try {
            byte[] canonical = json.writeValueAsBytes(canonicalize(definition));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (Exception exception) { throw new IllegalStateException("Cannot checksum topic definition", exception); }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node == null || node.isNull()) return JsonNodeFactory.instance.nullNode();
        if (node.isObject()) {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            List<String> names = new ArrayList<>(); node.fieldNames().forEachRemaining(names::add);
            names.sort(String::compareTo);
            for (String name : names) result.set(name, canonicalize(node.get(name)));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            for (JsonNode value : node) result.add(canonicalize(value));
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
