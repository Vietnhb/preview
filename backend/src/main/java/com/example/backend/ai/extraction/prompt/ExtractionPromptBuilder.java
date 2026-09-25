package com.example.backend.ai.extraction.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds bounded system and user messages for a candidate-routed extraction. */
public final class ExtractionPromptBuilder {
    private static final String CANDIDATE_RULES = "\nSelect only a supplied approved schema; do not invent one.\n";

    private final String basePrompt;
    private final int maxCandidates;
    private final int maxChars;
    private final ObjectMapper objectMapper;

    public ExtractionPromptBuilder(String basePrompt, int maxCandidates, int maxChars) {
        this(basePrompt, maxCandidates, maxChars, new ObjectMapper());
    }

    ExtractionPromptBuilder(String basePrompt, int maxCandidates, int maxChars, ObjectMapper objectMapper) {
        if (basePrompt == null || basePrompt.isBlank()) throw new IllegalArgumentException("basePrompt must not be blank.");
        if (maxCandidates < 1) throw new IllegalArgumentException("maxCandidates must be positive.");
        if (maxChars < 1) throw new IllegalArgumentException("maxChars must be positive.");
        this.basePrompt = basePrompt.trim();
        this.maxCandidates = maxCandidates;
        this.maxChars = maxChars;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Return the system prompt and request text as separate messages. Candidate
     * overflow is rejected so extraction and backend membership checks use one set.
     */
    public PromptMessages build(List<CandidateContractProjection> candidates, String requestText) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("At least one schema candidate is required.");
        }
        if (candidates.size() > maxCandidates) {
            throw new IllegalArgumentException("Candidate count exceeds the configured maximum of " + maxCandidates + ".");
        }
        if (candidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Schema candidates must not contain null.");
        }
        if (requestText == null || requestText.isBlank()) {
            throw new IllegalArgumentException("Request text must not be blank.");
        }

        String candidateJson = serialize(candidates.stream().map(this::candidateData).toList());
        boolean noLocalQuantities = candidates.stream().allMatch(candidate -> candidate.entityTypes().stream()
                .allMatch(type -> type.requiredQuantities().isEmpty() && type.optionalQuantities().isEmpty()));
        String systemMessage = basePrompt + CANDIDATE_RULES
                + "\nCandidate contracts (only these catalog versions may be selected):\n"
                + candidateJson
                + (noLocalQuantities ? "\nThis contract has no object-local quantities: every object's quantities=[]; put all physical quantities at the root.\n" : "");
        String userMessage = requestText.trim();
        if ((long) systemMessage.length() + userMessage.length() > maxChars) {
            throw new IllegalArgumentException("Candidate extraction prompt messages exceed the configured character limit.");
        }
        return new PromptMessages(systemMessage, userMessage);
    }

    public int messageCharacterCount(List<Map<String, Object>> messages) {
        if (messages == null) throw new IllegalArgumentException("Prompt messages are required.");
        int count = 0;
        for (Map<String, Object> message : messages) {
            Object content = message == null ? null : message.get("content");
            if (!(content instanceof String text)) {
                throw new IllegalArgumentException("Prompt message content must be text.");
            }
            count = Math.addExact(count, text.length());
        }
        return count;
    }

    private Map<String, Object> candidateData(CandidateContractProjection candidate) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaId", candidate.schemaId());
        result.put("schemaVersion", candidate.schemaVersion());
        result.put("topic", candidate.topic());
        result.put("name", candidate.name());
        result.put("modelId", candidate.modelId());
        result.put("requiredQuantities", candidate.requiredQuantities());
        result.put("optionalQuantities", candidate.optionalQuantities());
        result.put("relationTypes", candidate.relationTypes());
        result.put("endConditionCapabilities", candidate.endConditionCapabilities());
        result.put("entityTypes", candidate.entityTypes());
        result.put("executionDurationSeconds", candidate.executionDurationSeconds());
        result.put("rendererActorCapacity", candidate.rendererActorCapacity());
        return result;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize candidate extraction contracts.", exception);
        }
    }

    public record PromptMessages(String systemMessage, String userMessage) {
        public PromptMessages {
            if (systemMessage == null || systemMessage.isBlank()) throw new IllegalArgumentException("systemMessage must not be blank.");
            if (userMessage == null || userMessage.isBlank()) throw new IllegalArgumentException("userMessage must not be blank.");
        }
    }
}
