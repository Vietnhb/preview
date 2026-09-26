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
import com.example.backend.ai.extraction.model.ConversationTurn;
import com.example.backend.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AmbiguityResolutionApplier {
    private final ObjectMapper objectMapper;
    private final ExtractionProvider aiProvider;
    private final SpecificationReadinessService readinessService;

    public void applyAll(Specification specification, Map<String, String> answers) {
        applyAll(specification, answers, List.of());
    }

    public void applyAll(Specification specification, Map<String, String> answers,
            List<ConversationTurn> conversation) {
        if (!aiProvider.isAvailable()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "AI extraction provider is not configured");
        }
        Map<String, String> safeAnswers = answers == null ? Map.of() : Map.copyOf(answers);
        List<ConversationTurn> history = clarificationHistory(specification, safeAnswers, conversation);
        ObjectNode current = currentDocument(specification);
        ProviderExtractionResult result = aiProvider.resolveAmbiguities(
                specification.getSubmission().getEditableText(), current, safeAnswers, history);
        SpecificationDocument document = result.document();
        applyDocument(specification, document);
        specification.setValidationStatus("NOT_VALIDATED");
        specification.setValidationResult(null);
        specification.setClarificationConversation(objectMapper.valueToTree(history));
        synchronizeCases(specification, document, safeAnswers);
        readinessService.ensureRequiredAmbiguities(specification);
    }

    private List<ConversationTurn> clarificationHistory(Specification specification, Map<String, String> answers,
            List<ConversationTurn> supplied) {
        List<ConversationTurn> history = new java.util.ArrayList<>();
        if (specification.getClarificationConversation() != null) {
            history.addAll(objectMapper.convertValue(specification.getClarificationConversation(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<ConversationTurn>>() { }));
        } else if (supplied != null) {
            history.addAll(supplied);
        }
        for (var answer : answers.entrySet()) {
            // The persisted transcript is authoritative after the first accepted turn.
            if (specification.getClarificationConversation() == null && !history.isEmpty()
                    && history.getLast().role().equals("user") && history.getLast().text().equals(answer.getValue())) continue;
            specification.getAmbiguityCases().stream().filter(item -> item.getCode().equals(answer.getKey()))
                    .findFirst().ifPresent(item -> history.add(new ConversationTurn("assistant", item.getQuestion())));
            history.add(new ConversationTurn("user", answer.getValue()));
        }
        return List.copyOf(history);
    }

    private ObjectNode currentDocument(Specification specification) {
        ObjectNode current = objectMapper.createObjectNode();
        current.put("contractVersion", specification.getContractVersion());
        current.put("schemaVersion", specification.getSchemaVersion());
        current.put("topic", specification.getTopic());
        current.put("schemaId", specification.getSchemaId());
        current.set("objects", specification.getObjects());
        com.fasterxml.jackson.databind.node.ArrayNode quantities = objectMapper.createArrayNode();
        if (specification.getQuantities() != null && specification.getQuantities().isArray())
            specification.getQuantities().forEach(quantities::add);
        current.set("quantities", quantities);
        current.set("relations", specification.getRelations());
        if (specification.getEndCondition() != null)
            current.set("endCondition", specification.getEndCondition());
        current.put("confidence", specification.getConfidence());
        current.set("ambiguities", objectMapper.valueToTree(specification.getAmbiguityCases().stream()
                .filter(item -> item.getStatus() == AmbiguityStatus.OPEN)
                .sorted(SpecificationReadinessService.questionOrder(specification))
                .map(item -> new AmbiguityItem(item.getCode(), item.getFieldPath(), item.getQuestion(),
                        item.getOptions() == null || !item.getOptions().isArray() ? List.of()
                                : objectMapper.convertValue(item.getOptions(),
                                        new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { })))
                .toList()));
        return current;
    }

    private void synchronizeCases(Specification specification, SpecificationDocument document,
            Map<String, String> answers) {
        Map<String, AmbiguityItem> returned = new HashMap<>();
        document.ambiguities().forEach(item -> returned.put(item.code(), item));
        Set<String> existingIdentities = new HashSet<>();
        Instant now = Instant.now();
        for (AmbiguityCase existing : specification.getAmbiguityCases()) {
            String identity = existing.getCode();
            existingIdentities.add(identity);
            AmbiguityItem updated = returned.get(identity);
            if (updated != null) {
                existing.setQuestion(updated.question());
                existing.setFieldPath(updated.fieldPath());
                existing.setOptions(objectMapper.valueToTree(updated.options()));
                existing.setStatus(AmbiguityStatus.OPEN);
                existing.setResolution(null);
                existing.setResolvedAt(null);
            } else if (existing.getStatus() == AmbiguityStatus.OPEN) {
                existing.setResolution(answers.getOrDefault(existing.getCode(), "Resolved by the model from the conversation."));
                existing.setStatus(AmbiguityStatus.RESOLVED);
                existing.setResolvedAt(now);
            }
        }
        for (AmbiguityItem item : document.ambiguities()) {
            if (existingIdentities.contains(item.code()))
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

    private void applyDocument(Specification specification, SpecificationDocument document) {
        specification.setContractVersion(SpecificationDocument.CURRENT_SCHEMA_VERSION);
        specification.setSchemaVersion(document.schemaVersion());
        specification.setTopic(document.topic());
        specification.setSchemaId(document.schemaId());
        specification.setObjects(objectMapper.valueToTree(document.objects()));
        specification.setQuantities(objectMapper.valueToTree(document.quantities()));
        specification.setRelations(objectMapper.valueToTree(document.relations()));
        specification.setConfidence(document.confidence());
        specification.setEndCondition(document.endCondition());
        specification.setAmbiguity(objectMapper.valueToTree(document.ambiguities()));
    }
}
