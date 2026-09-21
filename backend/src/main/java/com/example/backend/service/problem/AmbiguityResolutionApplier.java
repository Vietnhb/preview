package com.example.backend.service.problem;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.example.backend.entity.problem.AmbiguityCase;
import com.example.backend.entity.enums.AmbiguityStatus;
import com.example.backend.entity.enums.ConfirmationState;
import com.example.backend.entity.problem.Specification;
import com.example.backend.ai.extraction.ExtractionProvider;
import com.example.backend.ai.extraction.model.AmbiguityItem;
import com.example.backend.ai.extraction.model.ProviderExtractionResult;
import com.example.backend.ai.extraction.model.SpecificationDocument;
import com.example.backend.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AmbiguityResolutionApplier {
    private final ObjectMapper objectMapper;
    private final ExtractionProvider aiProvider;
    private final SchemaDefinitionService schemaDefinitions;
    private final SpecificationReadinessService readinessService;

    public void applyAll(Specification specification, Map<String, String> answers) {
        if (!aiProvider.isAvailable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI extraction provider is not configured");
        }
        Map<String, String> safeAnswers = answers == null ? Map.of() : Map.copyOf(answers);
        ObjectNode current = currentDocument(specification);
        ProviderExtractionResult result = aiProvider.resolveAmbiguities(
                specification.getSubmission().getEditableText(), current, safeAnswers);
        applyDocument(specification, result.document());
        synchronizeCases(specification, result.document(), safeAnswers);
        readinessService.ensureRequiredAmbiguities(specification);
    }

    private ObjectNode currentDocument(Specification specification) {
        ObjectNode current = objectMapper.createObjectNode();
        current.put("contractVersion", specification.getContractVersion());
        current.put("schemaVersion", specification.getSchemaVersion());
        current.put("topic", specification.getTopic());
        current.put("schemaId", specification.getSchemaId());
        current.set("objects", specification.getObjects());
        com.fasterxml.jackson.databind.node.ArrayNode quantities = objectMapper.createArrayNode();
        if (specification.getQuantities() != null && specification.getQuantities().isArray()) {
            specification.getQuantities().forEach(source -> {
                ObjectNode quantity = source.deepCopy();
                quantity.remove(List.of("normalizedValue", "normalizedUnit"));
                quantities.add(quantity);
            });
        }
        current.set("quantities", quantities);
        current.set("relations", specification.getRelations());
        if (specification.getEndCondition() != null)
            current.set("endCondition", specification.getEndCondition());
        current.put("confidence", specification.getConfidence());
        current.set("ambiguities", specification.getAmbiguity());
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
                existing.setQuestion(updated.question());
                existing.setFieldPath(updated.fieldPath());
                existing.setOptions(objectMapper.valueToTree(updated.options()));
                existing.setStatus(AmbiguityStatus.OPEN);
                existing.setResolution(null);
                existing.setResolvedAt(null);
            } else if (answers.containsKey(existing.getCode()) && existing.getStatus() == AmbiguityStatus.OPEN) {
                existing.setResolution(answers.get(existing.getCode()).trim());
                existing.setStatus(AmbiguityStatus.RESOLVED);
                existing.setResolvedAt(now);
            }
        }
        for (AmbiguityItem item : document.ambiguities()) {
            if (existingIdentities.contains(identity(item.fieldPath(), item.code())))
                continue;
            AmbiguityCase created = new AmbiguityCase();
            created.setCode(item.code());
            created.setFieldPath(item.fieldPath());
            created.setQuestion(item.question());
            created.setOptions(objectMapper.valueToTree(item.options()));
            created.setStatus(AmbiguityStatus.OPEN);
            specification.addAmbiguityCase(created);
        }
        boolean open = specification.getAmbiguityCases().stream()
                .anyMatch(item -> item.getStatus() == AmbiguityStatus.OPEN);
        specification.setConfirmationState(open ? ConfirmationState.UNRESOLVED : ConfirmationState.CONFIRMED);
    }

    private String identity(String fieldPath, String code) {
        String value = fieldPath == null || fieldPath.isBlank() ? code : fieldPath;
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private void applyDocument(Specification specification, SpecificationDocument document) {
        specification.setContractVersion(SpecificationDocument.CURRENT_SCHEMA_VERSION);
        var schema = schemaDefinitions.requireCurrentApproved(document.schemaId(), document.schemaVersion());
        specification.setSchemaVersion(schema.getVersion());
        specification.setTopic(document.topic());
        specification.setSchemaId(document.schemaId());
        specification.setObjects(objectMapper.valueToTree(document.objects()));
        specification.setQuantities(schemaDefinitions.canonicalizeQuantities(
                objectMapper.valueToTree(document.quantities()), schema.getDefinition()));
        specification.setRelations(objectMapper.valueToTree(document.relations()));
        specification.setConfidence(document.confidence());
        specification.setEndCondition(document.endCondition());
        specification.setAmbiguity(objectMapper.valueToTree(document.ambiguities()));
    }
}
