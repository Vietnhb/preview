package com.example.backend.service.problem;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.backend.entity.enums.AmbiguityStatus;
import com.example.backend.entity.enums.ConfirmationState;
import com.example.backend.entity.problem.Specification;
import com.example.backend.ai.extraction.model.ConversationTurn;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpecificationReadinessService {
    private final ObjectMapper objectMapper;

    private record AmbiguityView(String code, String fieldPath, String question, JsonNode options) { }

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
        return List.copyOf(blockers);
    }

    public void ensureRequiredAmbiguities(Specification specification) {
        ensureRequiredAmbiguities(specification, List.of());
    }

    public void ensureRequiredAmbiguities(Specification specification, List<ConversationTurn> ignoredConversation) {
        if (specification.getConfirmationState() == ConfirmationState.REJECTED) return;
        boolean open = specification.getAmbiguityCases().stream().anyMatch(item -> item.getStatus() == AmbiguityStatus.OPEN);
        if (open) {
            specification.setConfirmationState(ConfirmationState.UNRESOLVED);
        }
        specification.setAmbiguity(objectMapper.valueToTree(specification.getAmbiguityCases().stream()
                .filter(item -> item.getStatus() == AmbiguityStatus.OPEN)
                .map(item -> new AmbiguityView(item.getCode(), item.getFieldPath(), item.getQuestion(), item.getOptions()))
                .toList()));
    }

    public JsonNode toJson(Specification specification) {
        ObjectNode node = objectMapper.createObjectNode();
        if (specification.getSchemaVersion() != null) node.put("schemaVersion", specification.getSchemaVersion());
        if (specification.getContractVersion() != null) node.put("contractVersion", specification.getContractVersion());
        if (specification.getSchemaId() != null) node.put("schemaId", specification.getSchemaId());
        if (specification.getTopic() != null) node.put("topic", specification.getTopic());
        node.set("objects", specification.getObjects());
        node.set("quantities", specification.getQuantities());
        node.set("relations", specification.getRelations());
        if (specification.getEndCondition() != null) node.set("endCondition", specification.getEndCondition());
        if (specification.getConfidence() != null) node.put("confidence", specification.getConfidence());
        node.set("ambiguities", specification.getAmbiguity());
        return node;
    }
}
