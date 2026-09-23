package com.example.backend.service.problem;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.example.backend.entity.enums.AmbiguityStatus;
import com.example.backend.entity.enums.ConfirmationState;
import com.example.backend.entity.problem.AmbiguityCase;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.entity.problem.Specification;
import com.example.backend.ai.extraction.ExtractionProvider;
import com.example.backend.ai.extraction.model.AmbiguityItem;
import com.example.backend.ai.extraction.model.ConversationTurn;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpecificationReadinessService {
    private static final BigDecimal MAX_UNRESOLVED_CONFIDENCE = new BigDecimal("0.8500");

    private final SchemaDefinitionService schemas;
    private final ObjectMapper objectMapper;
    private final ExtractionProvider aiProvider;

    public List<String> blockers(Specification specification) {
        List<String> blockers = new ArrayList<>();
        if (specification.getConfirmationState() == ConfirmationState.REJECTED) {
            blockers.add("Teacher declined the compatibility proposal");
            return List.copyOf(blockers);
        }
        if (specification.getConfirmationState() == ConfirmationState.UNRESOLVED
                || specification.getAmbiguityCases().stream().anyMatch(item -> item.getStatus() == AmbiguityStatus.OPEN)) {
            blockers.add("Unresolved ambiguity cases remain");
        }
        if (!StringUtils.hasText(specification.getSchemaId())) blockers.add("Schema is missing");
        else {
            SchemaVersion schema = schemas.requirePublishedVersion(specification.getSchemaId(), specification.getSchemaVersion());
            blockers.addAll(schemas.validateSpecification(toJson(specification), schema.getDefinition()));
        }
        return List.copyOf(blockers);
    }

    public void ensureRequiredAmbiguities(Specification specification) {
        ensureRequiredAmbiguities(specification, List.of());
    }

    public void ensureRequiredAmbiguities(Specification specification, List<ConversationTurn> conversation) {
        if (specification.getConfirmationState() == ConfirmationState.REJECTED) return;
        if (!StringUtils.hasText(specification.getSchemaId())) return;
        SchemaVersion schema = schemas.requirePublishedVersion(specification.getSchemaId(), specification.getSchemaVersion());
        removeAmbiguousRequiredQuantities(specification, schema.getDefinition());
        List<SchemaDefinitionService.RequiredGap> gaps = schemas.missingRequiredQuantities(toJson(specification), schema.getDefinition());
        boolean compatibilityPending = specification.getAmbiguityCases().stream()
                .anyMatch(item -> item.getStatus() == AmbiguityStatus.OPEN
                        && !isRequiredInputPath(item.getFieldPath()));
        if (compatibilityPending) gaps = List.of();
        else canonicalizeOpenAmbiguities(specification, gaps);
        List<AmbiguityItem> generatedQuestions = gaps.isEmpty() ? List.of()
                : aiProvider.phraseVerificationQuestions(specification.getSubmission().getEditableText(),
                        gaps.stream().map(this::requiredGapFinding).toList(), conversation);
        for (SchemaDefinitionService.RequiredGap gap : gaps) {
            String code = "schema.required." + gap.key();
            boolean exists = specification.getAmbiguityCases().stream().anyMatch(item -> item.getStatus() == AmbiguityStatus.OPEN
                    && item.getCode().equals(code));
            if (exists) continue;
            AmbiguityCase ambiguity = new AmbiguityCase();
            ambiguity.setCode(code);
            ambiguity.setFieldPath(gap.key().startsWith("objects.")
                    ? gap.key() : "quantities." + gap.key());
            String fieldPath = ambiguity.getFieldPath();
            AmbiguityItem generated = generatedQuestions.stream()
                    .filter(item -> fieldPath.equals(item.fieldPath())).findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "AI clarification did not cover required input " + fieldPath));
            ambiguity.setQuestion(generated.question());
            ambiguity.setOptions(objectMapper.createArrayNode());
            ambiguity.setStatus(AmbiguityStatus.OPEN);
            specification.addAmbiguityCase(ambiguity);
        }
        boolean open = specification.getAmbiguityCases().stream().anyMatch(item -> item.getStatus() == AmbiguityStatus.OPEN);
        if (open) {
            specification.setConfirmationState(ConfirmationState.UNRESOLVED);
            if (specification.getConfidence() == null
                    || specification.getConfidence().compareTo(MAX_UNRESOLVED_CONFIDENCE) > 0) {
                specification.setConfidence(MAX_UNRESOLVED_CONFIDENCE);
            }
        }
        specification.setAmbiguity(objectMapper.valueToTree(specification.getAmbiguityCases().stream()
                .filter(item -> item.getStatus() == AmbiguityStatus.OPEN)
                .map(item -> new AmbiguityView(item.getCode(), item.getFieldPath(), item.getQuestion(), item.getOptions()))
                .toList()));
    }

    private boolean isRequiredInputPath(String fieldPath) {
        return fieldPath != null && (fieldPath.startsWith("quantities.")
                || fieldPath.startsWith("objects.") && fieldPath.contains(".quantities."));
    }

    private String requiredGapFinding(SchemaDefinitionService.RequiredGap gap) {
        String fieldPath = gap.key().startsWith("objects.") ? gap.key() : "quantities." + gap.key();
        return "issue=REQUIRED_INPUT_MISSING; fieldPath=" + fieldPath + "; expectedUnit=" + gap.unit()
                + "; guidance=Ask for this required value in the user's language and context. Include the expected unit. "
                + "Use the original request and current specification to identify the right object. Do not invent a value.";
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
            if (ambiguity.getStatus() != AmbiguityStatus.OPEN) continue;
            SchemaDefinitionService.RequiredGap gap = gaps.stream()
                    .filter(candidate -> refersTo(ambiguity, candidate.key())).findFirst().orElse(null);
            String code;
            if (gap != null) {
                code = "schema.required." + gap.key();
                ambiguity.setFieldPath(fieldPathFor(gap.key()));
            } else code = semanticCode(ambiguity);
            if (seen.add(code)) ambiguity.setCode(code);
            else {
                ambiguity.setStatus(AmbiguityStatus.RESOLVED);
                ambiguity.setResolution("Merged with canonical ambiguity " + code);
                ambiguity.setResolvedAt(java.time.Instant.now());
            }
        }
    }

    private boolean refersTo(AmbiguityCase ambiguity, String key) {
        Set<String> source = tokens(String.join(" ", safe(ambiguity.getCode()), safe(ambiguity.getFieldPath())));
        Set<String> required = tokens(key);
        return !required.isEmpty() && source.containsAll(required);
    }

    private String fieldPathFor(String key) {
        return key != null && key.startsWith("objects.") ? key : "quantities." + key;
    }

    private String semanticCode(AmbiguityCase ambiguity) {
        String existingCode = safe(ambiguity.getCode()).toLowerCase(Locale.ROOT);
        if (existingCode.startsWith("jev.capacity.")) return ambiguity.getCode();
        String identity = StringUtils.hasText(ambiguity.getFieldPath()) ? ambiguity.getFieldPath() : ambiguity.getCode();
        String slug = String.join(".", tokens(identity));
        return "ai." + (StringUtils.hasText(slug) ? slug : "unspecified");
    }

    private Set<String> tokens(String value) {
        Set<String> result = new java.util.LinkedHashSet<>();
        for (String token : safe(value).toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (!token.isBlank() && !token.equals("quantities") && !token.equals("objects") && !token.equals("object")) {
                result.add(token);
            }
        }
        return result;
    }

    private String safe(String value) { return value == null ? "" : value; }

    private record AmbiguityView(String code, String fieldPath, String question, JsonNode options) { }

    public JsonNode toJson(Specification specification) {
        ObjectNode node = objectMapper.createObjectNode();
        putText(node, "schemaVersion", specification.getSchemaVersion());
        putText(node, "contractVersion", specification.getContractVersion());
        JsonNode definition = null;
        if (StringUtils.hasText(specification.getSchemaId())) {
            SchemaVersion pinned = schemas.requirePublishedVersion(
                    specification.getSchemaId(), specification.getSchemaVersion());
            putText(node, "schemaId", pinned.getSchemaId());
            definition = pinned.getDefinition();
            putText(node, "model", definition.path("model").asText());
        } else putText(node, "schemaId", specification.getSchemaId());
        putText(node, "topic", specification.getTopic());
        node.set("objects", specification.getObjects());
        node.set("quantities", definition == null ? specification.getQuantities()
                : schemas.canonicalizeQuantities(specification.getQuantities(), definition));
        if (definition != null) node = (ObjectNode) schemas.materializeDefaults(node, definition);
        node.set("relations", specification.getRelations());
        if (specification.getEndCondition() != null) node.set("endCondition", specification.getEndCondition());
        node.set("ambiguities", specification.getAmbiguity());
        return node;
    }

    private void putText(ObjectNode node, String key, String value) {
        node.set(key, value == null ? objectMapper.nullNode() : objectMapper.getNodeFactory().textNode(value));
    }
}
