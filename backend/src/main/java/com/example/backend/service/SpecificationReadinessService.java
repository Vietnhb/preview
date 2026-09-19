package com.example.backend.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.backend.enums.AmbiguityStatus;
import com.example.backend.enums.ConfirmationState;
import com.example.backend.entity.Specification;
import com.example.backend.entity.AmbiguityCase;
import com.example.backend.entity.SchemaVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpecificationReadinessService {
    private final SchemaDefinitionService schemas;
    private final ObjectMapper objectMapper;

    public List<String> blockers(Specification specification) {
        List<String> blockers = new ArrayList<>();
        if (specification.getConfirmationState() == ConfirmationState.UNRESOLVED
                || specification.getAmbiguityCases().stream().anyMatch(item -> item.getStatus() == AmbiguityStatus.OPEN)) {
            blockers.add("Unresolved ambiguity cases remain");
        }
        if (!StringUtils.hasText(specification.getSchemaId())) blockers.add("Schema is missing");
        else {
            SchemaVersion schema = schemas.requireApproved(specification.getSchemaId(), specification.getSchemaVersion());
            blockers.addAll(schemas.validateSpecification(toJson(specification), schema.getDefinition()));
        }
        return List.copyOf(blockers);
    }

    public void ensureRequiredAmbiguities(Specification specification) {
        if (!StringUtils.hasText(specification.getSchemaId())) return;
        SchemaVersion schema = schemas.requireApproved(specification.getSchemaId(), specification.getSchemaVersion());
        removeAmbiguousRequiredQuantities(specification, schema.getDefinition());
        List<SchemaDefinitionService.RequiredGap> gaps = schemas.missingRequiredQuantities(
                toJson(specification), schema.getDefinition());
        canonicalizeOpenAmbiguities(specification, gaps);
        for (SchemaDefinitionService.RequiredGap gap : gaps) {
            String code = "schema.required." + gap.key();
            boolean exists = specification.getAmbiguityCases().stream().anyMatch(item -> item.getStatus() == AmbiguityStatus.OPEN
                    && item.getCode().equals(code));
            if (exists) continue;
            AmbiguityCase ambiguity = new AmbiguityCase(); ambiguity.setCode(code); ambiguity.setFieldPath("quantities." + gap.key());
            ambiguity.setQuestion("Đề bài chưa xác định " + gap.key() + ". Vui lòng cung cấp giá trị và đơn vị " + gap.unit() + ".");
            ambiguity.setOptions(objectMapper.createArrayNode()); ambiguity.setStatus(AmbiguityStatus.OPEN);
            specification.addAmbiguityCase(ambiguity);
        }
        boolean open = specification.getAmbiguityCases().stream().anyMatch(item -> item.getStatus() == AmbiguityStatus.OPEN);
        if (open) specification.setConfirmationState(ConfirmationState.UNRESOLVED);
        specification.setAmbiguity(objectMapper.valueToTree(specification.getAmbiguityCases().stream()
                .filter(item -> item.getStatus() == AmbiguityStatus.OPEN)
                .map(item -> new AmbiguityView(item.getCode(), item.getFieldPath(), item.getQuestion(), item.getOptions()))
                .toList()));
    }

    private void removeAmbiguousRequiredQuantities(Specification specification, JsonNode definition) {
        Set<String> ambiguousKeys = new HashSet<>();
        for (JsonNode required : definition.path("requiredQuantities")) {
            String key = required.path("key").asText();
            boolean unresolved = specification.getAmbiguityCases().stream()
                    .anyMatch(item -> item.getStatus() == AmbiguityStatus.OPEN && refersTo(item, key));
            if (unresolved) ambiguousKeys.add(key);
        }
        if (ambiguousKeys.isEmpty() || specification.getQuantities() == null
                || !specification.getQuantities().isArray()) return;

        Set<String> rejectedNames = new HashSet<>();
        for (JsonNode required : definition.path("requiredQuantities")) {
            if (!ambiguousKeys.contains(required.path("key").asText())) continue;
            rejectedNames.add(required.path("key").asText().toLowerCase(Locale.ROOT));
            required.path("aliases").forEach(alias -> rejectedNames.add(alias.asText().toLowerCase(Locale.ROOT)));
        }
        ArrayNode sanitized = objectMapper.createArrayNode();
        for (JsonNode quantity : specification.getQuantities()) {
            String name = quantity.path("name").asText().toLowerCase(Locale.ROOT);
            String symbol = quantity.path("symbol").asText().toLowerCase(Locale.ROOT);
            if (!rejectedNames.contains(name) && !rejectedNames.contains(symbol)) sanitized.add(quantity);
        }
        specification.setQuantities(sanitized);
    }

    private void canonicalizeOpenAmbiguities(Specification specification,
            List<SchemaDefinitionService.RequiredGap> gaps) {
        Set<String> seen = new HashSet<>();
        for (AmbiguityCase ambiguity : specification.getAmbiguityCases()) {
            if (ambiguity.getStatus() == AmbiguityStatus.OPEN) {
                SchemaDefinitionService.RequiredGap gap = gaps.stream()
                        .filter(candidate -> refersTo(ambiguity, candidate.key()))
                        .findFirst().orElse(null);
                String code;
                if (gap != null) {
                    code = "schema.required." + gap.key();
                    ambiguity.setFieldPath("quantities." + gap.key());
                } else {
                    code = semanticCode(ambiguity);
                }
                if (seen.add(code)) {
                    ambiguity.setCode(code);
                } else {
                    ambiguity.setStatus(AmbiguityStatus.RESOLVED);
                    ambiguity.setResolution("Merged with canonical ambiguity " + code);
                    ambiguity.setResolvedAt(java.time.Instant.now());
                }
            }
        }
    }

    private boolean refersTo(AmbiguityCase ambiguity, String key) {
        Set<String> source = tokens(String.join(" ", safe(ambiguity.getCode()), safe(ambiguity.getFieldPath())));
        Set<String> required = tokens(key);
        return !required.isEmpty() && source.containsAll(required);
    }

    private String semanticCode(AmbiguityCase ambiguity) {
        String identity = StringUtils.hasText(ambiguity.getFieldPath())
                ? ambiguity.getFieldPath() : ambiguity.getCode();
        String slug = String.join(".", tokens(identity));
        return "ai." + (StringUtils.hasText(slug) ? slug : "unspecified");
    }

    private Set<String> tokens(String value) {
        Set<String> result = new java.util.LinkedHashSet<>();
        for (String token : safe(value).toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!token.isBlank() && !token.equals("quantities") && !token.equals("objects")
                    && !token.equals("object")) result.add(token);
        }
        return result;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record AmbiguityView(String code, String fieldPath, String question, JsonNode options) { }

    public JsonNode toJson(Specification specification) {
        ObjectNode node = objectMapper.createObjectNode();
        putText(node, "schemaId", specification.getSchemaId());
        putText(node, "schemaVersion", specification.getSchemaVersion());
        putText(node, "contractVersion", specification.getContractVersion());
        if (StringUtils.hasText(specification.getSchemaId())) putText(node, "model",
                schemas.requireApproved(specification.getSchemaId(), specification.getSchemaVersion()).getDefinition().path("model").asText());
        putText(node, "topic", specification.getTopic());
        node.set("objects", specification.getObjects());
        node.set("quantities", specification.getQuantities());
        node.set("relations", specification.getRelations());
        if (specification.getEndCondition() != null) node.set("endCondition", specification.getEndCondition());
        node.set("ambiguities", specification.getAmbiguity());
        return node;
    }

    private void putText(ObjectNode node, String key, String value) {
        node.set(key, value == null ? objectMapper.nullNode() : objectMapper.getNodeFactory().textNode(value));
    }
}
