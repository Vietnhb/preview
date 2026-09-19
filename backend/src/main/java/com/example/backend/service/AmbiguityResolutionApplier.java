package com.example.backend.service;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.backend.entity.AmbiguityCase;
import com.example.backend.enums.AmbiguityStatus;
import com.example.backend.enums.ConfirmationState;
import com.example.backend.entity.Specification;
import com.example.backend.extraction.AmbiguityItem;
import com.example.backend.extraction.OpenRouterExtractionProvider;
import com.example.backend.extraction.ProviderExtractionResult;
import com.example.backend.extraction.PhysicalQuantity;
import com.example.backend.extraction.RuleBasedExtractionProvider;
import com.example.backend.extraction.SpecificationDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AmbiguityResolutionApplier {
    private final ObjectMapper objectMapper;
    private final OpenRouterExtractionProvider aiProvider;
    private final RuleBasedExtractionProvider ruleBasedProvider;
    private final SchemaDefinitionService schemaDefinitions;
    private final SpecificationReadinessService readinessService;

    public void applyAll(Specification specification, Map<String, String> answers) {
        if (!aiProvider.isAvailable()) {
            applyRuleBased(specification, answers);
            return;
        }
        ObjectNode current = currentDocument(specification);
        try {
            ProviderExtractionResult result = aiProvider.resolveAmbiguities(
                    specification.getSubmission().getEditableText(), current, Map.copyOf(answers));
            applyDocument(specification, result.document());
            synchronizeCases(specification, result.document(), answers);
        } catch (RuntimeException aiFailure) {
            applyRuleBased(specification, answers);
        }
    }

    private void applyRuleBased(Specification specification, Map<String, String> answers) {
        if (answers == null || answers.isEmpty()) throw new IllegalArgumentException("Ambiguity answers are required.");
        JsonNode source = specification.getQuantities();
        ArrayNode quantities = source != null && source.isArray()
                ? (ArrayNode) source.deepCopy() : objectMapper.createArrayNode();
        int resolvedCount = 0;
        for (AmbiguityCase ambiguity : specification.getAmbiguityCases()) {
            String answer = answers.get(ambiguity.getCode());
            // The reviewer endpoint resolves one case at a time. The teacher
            // batch endpoint validates that all cases were answered before it
            // reaches this method, so unrelated OPEN cases remain untouched.
            if (ambiguity.getStatus() == AmbiguityStatus.OPEN
                    && org.springframework.util.StringUtils.hasText(answer)) {
                applyRuleBasedAnswer(ambiguity, answer, quantities);
                resolvedCount++;
            }
        }
        if (resolvedCount == 0) throw new IllegalArgumentException("No open ambiguity matched the supplied answer.");
        specification.setQuantities(quantities);
        readinessService.ensureRequiredAmbiguities(specification);
        boolean open = specification.getAmbiguityCases().stream().anyMatch(item -> item.getStatus() == AmbiguityStatus.OPEN);
        specification.setConfirmationState(open ? ConfirmationState.UNRESOLVED : ConfirmationState.CONFIRMED);
    }

    private void applyRuleBasedAnswer(AmbiguityCase ambiguity, String answer, ArrayNode quantities) {
            String prefix = "quantities.";
            if (ambiguity.getFieldPath() == null || !ambiguity.getFieldPath().startsWith(prefix)) {
                throw new IllegalArgumentException("This ambiguity needs an AI-capable confirmation provider.");
            }
            String key = ambiguity.getFieldPath().substring(prefix.length()).trim();
            PhysicalQuantity parsed = ruleBasedProvider.explicitQuantity(key, answer);
            for (int index = quantities.size() - 1; index >= 0; index--) {
                JsonNode item = quantities.get(index);
                if (key.equalsIgnoreCase(item.path("name").asText())) quantities.remove(index);
            }
            quantities.add(objectMapper.valueToTree(parsed));
            ambiguity.setResolution(answer.trim());
            ambiguity.setStatus(AmbiguityStatus.RESOLVED);
            ambiguity.setResolvedAt(java.time.Instant.now());
    }

    private ObjectNode currentDocument(Specification specification) {
        ObjectNode current = objectMapper.createObjectNode();
        current.put("schemaVersion", specification.getContractVersion()); current.put("topic", specification.getTopic());
        current.put("schemaId", specification.getSchemaId()); current.set("objects", specification.getObjects());
        current.set("quantities", specification.getQuantities()); current.set("relations", specification.getRelations());
        if (specification.getEndCondition() != null) current.set("endCondition", specification.getEndCondition());
        current.put("confidence", specification.getConfidence()); current.set("ambiguities", specification.getAmbiguity());
        return current;
    }

    private void synchronizeCases(Specification specification, SpecificationDocument document,
            Map<String, String> answers) {
        Map<String, AmbiguityItem> returned = new HashMap<>();
        document.ambiguities().forEach(item -> returned.put(identity(item.fieldPath(), item.code()), item));
        Set<String> existingIdentities = new HashSet<>();
        Instant now = Instant.now();
        for (AmbiguityCase existing : specification.getAmbiguityCases()) {
            String identity = identity(existing.getFieldPath(), existing.getCode());
            existingIdentities.add(identity);
            AmbiguityItem updated = returned.get(identity);
            if (updated != null) {
                existing.setQuestion(updated.question()); existing.setFieldPath(updated.fieldPath());
                existing.setOptions(objectMapper.valueToTree(updated.options())); existing.setStatus(AmbiguityStatus.OPEN);
                existing.setResolution(null); existing.setResolvedAt(null);
            } else if (answers.containsKey(existing.getCode()) && existing.getStatus() == AmbiguityStatus.OPEN) {
                existing.setResolution(answers.get(existing.getCode()).trim());
                existing.setStatus(AmbiguityStatus.RESOLVED); existing.setResolvedAt(now);
            }
        }
        for (AmbiguityItem item : document.ambiguities()) {
            if (existingIdentities.contains(identity(item.fieldPath(), item.code()))) continue;
            AmbiguityCase created = new AmbiguityCase(); created.setCode(item.code()); created.setFieldPath(item.fieldPath());
            created.setQuestion(item.question()); created.setOptions(objectMapper.valueToTree(item.options()));
            created.setStatus(AmbiguityStatus.OPEN); specification.addAmbiguityCase(created);
        }
        boolean open = specification.getAmbiguityCases().stream().anyMatch(item -> item.getStatus() == AmbiguityStatus.OPEN);
        specification.setConfirmationState(open ? ConfirmationState.UNRESOLVED : ConfirmationState.CONFIRMED);
    }

    private String identity(String fieldPath, String code) {
        String value = fieldPath == null || fieldPath.isBlank() ? code : fieldPath;
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void applyDocument(Specification specification, SpecificationDocument document) {
        specification.setContractVersion(SpecificationDocument.CURRENT_SCHEMA_VERSION);
        specification.setSchemaVersion(schemaDefinitions.requireApproved(document.schemaId()).getVersion());
        specification.setTopic(document.topic());
        specification.setSchemaId(document.schemaId()); specification.setObjects(objectMapper.valueToTree(document.objects()));
        specification.setQuantities(objectMapper.valueToTree(document.quantities()));
        specification.setRelations(objectMapper.valueToTree(document.relations())); specification.setConfidence(document.confidence());
        specification.setEndCondition(document.endCondition());
        specification.setAmbiguity(objectMapper.valueToTree(document.ambiguities()));
    }
}
