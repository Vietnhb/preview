package com.example.backend.dto.problem;

import java.util.Map;
import java.util.List;

import com.example.backend.ai.extraction.model.ConversationTurn;

public record ConfirmProblemRequest(Map<String, String> answers, List<ConversationTurn> conversation) {
    public ConfirmProblemRequest(Map<String, String> answers) {
        this(answers, List.of());
    }
}
