package com.example.backend.system.problem.service;

import com.example.backend.system.problem.model.entity.Specification;
import com.example.backend.system.problem.model.enums.AmbiguityStatus;
import com.example.backend.system.problem.model.enums.ConfirmationState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
        if (specification.getConfirmationState() == ConfirmationState.REJECTED) return;
        boolean open = specification.getAmbiguityCases().stream().anyMatch(item -> item.getStatus() == AmbiguityStatus.OPEN);
        if (open) {
            specification.setConfirmationState(ConfirmationState.UNRESOLVED);
        }
        specification.setAmbiguity(objectMapper.valueToTree(specification.getAmbiguityCases().stream()
                .filter(item -> item.getStatus() == AmbiguityStatus.OPEN)
                .sorted(specification.questionOrder())
                .map(item -> new AmbiguityView(item.getCode(), item.getFieldPath(), item.getQuestion(), item.getOptions()))
                .toList()));
    }

}
