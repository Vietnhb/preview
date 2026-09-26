package com.example.backend.ai.extraction.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Builds bounded system and user messages for a candidate-routed extraction. */
public final class ExtractionPromptBuilder {
    private static final String CANDIDATE_RULES = """

            Select only a supplied approved capability contract; never invent an identity.
            A routing result is a candidate set, not proof that any candidate supports the request. If the available
            descriptions do not establish a clear fit, ask the most useful clarification before completing a spec.
            Missing values, conflicting facts, and model details are clarification questions, not reasons to reject
            an otherwise capable contract. If none can express a requested behavior, explain that limitation and
            ask a focused question about an acceptable alternative.
            Use each pack's object types, relation types, quantities, units, laws and applicability as data. A
            quantity definition names a physical concept and its unit; it does not supply a numerical value.
            Check each law's stated application requirements before using it. Do not assume fields from another
            pack. Core type references are optional aids and do not add requirements.
            Ask at most one clarification question in a response: choose the most consequential unresolved point.
            Write every clarification question and option in the same natural language as the user's description.
            An English pack or router assessment must not change that language; for Vietnamese input, use natural Vietnamese.
            """;

    private final String basePrompt;
    private final int maxChars;
    private final ObjectMapper objectMapper;

    public ExtractionPromptBuilder(String basePrompt, int maxChars) {
        this(basePrompt, maxChars, new ObjectMapper());
    }

    ExtractionPromptBuilder(String basePrompt, int maxChars, ObjectMapper objectMapper) {
        if (basePrompt == null || basePrompt.isBlank()) throw new IllegalArgumentException("basePrompt must not be blank.");
        if (maxChars < 1) throw new IllegalArgumentException("maxChars must be positive.");
        this.basePrompt = basePrompt.trim();
        this.maxChars = maxChars;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Return the system prompt and request text as separate messages. The prompt
     * character budget is the only catalog-size bound; no fixed candidate count
     * silently removes capabilities from routing.
     */
    public PromptMessages build(List<CandidateContractProjection> candidates, String requestText) {
        return build(candidates, requestText, null);
    }

    /** Adds a generic routing confidence assessment without narrowing pack data or encoding topic rules. */
    public PromptMessages build(List<CandidateContractProjection> candidates, String requestText,
            String routingAssessment) {
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalArgumentException("At least one schema candidate is required.");
        }
        if (candidates.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Schema candidates must not contain null.");
        }
        if (requestText == null || requestText.isBlank()) {
            throw new IllegalArgumentException("Request text must not be blank.");
        }

        Set<String> sharedCapabilityKeys = sharedCapabilityKeys(candidates);
        Map<String, Object> contractBundle = new LinkedHashMap<>();
        contractBundle.put("sharedCapabilities", sharedCapabilities(candidates, sharedCapabilityKeys));
        contractBundle.put("candidates", candidates.stream()
                .map(candidate -> candidateData(candidate, sharedCapabilityKeys)).toList());
        String candidateJson = serialize(contractBundle);
        String systemMessage = basePrompt + CANDIDATE_RULES
                + "\nCapability contracts (shared details apply to every candidate; only these catalog versions may be selected):\n"
                + candidateJson;
        if (routingAssessment != null && !routingAssessment.isBlank()) {
            systemMessage += "\nRouting assessment (use as uncertainty context only; verify behavior against the supplied contracts):\n"
                    + routingAssessment.trim();
        }
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

    private Set<String> sharedCapabilityKeys(List<CandidateContractProjection> candidates) {
        List<String> possible = List.of("limitations", "inputPolicy", "output");
        Set<String> shared = new java.util.LinkedHashSet<>();
        JsonNode first = candidates.getFirst().capabilities();
        if (first == null || !first.isObject()) return shared;
        for (String key : possible) {
            JsonNode value = first.get(key);
            if (value != null && candidates.stream().allMatch(candidate -> candidate.capabilities() != null
                    && value.equals(candidate.capabilities().get(key)))) {
                shared.add(key);
            }
        }
        return Set.copyOf(shared);
    }

    private Map<String, Object> sharedCapabilities(List<CandidateContractProjection> candidates,
            Set<String> sharedKeys) {
        Map<String, Object> shared = new LinkedHashMap<>();
        JsonNode first = candidates.getFirst().capabilities();
        if (first == null) return shared;
        for (String key : sharedKeys) shared.put(key, first.get(key));
        return shared;
    }

    private Map<String, Object> candidateData(CandidateContractProjection candidate, Set<String> sharedKeys) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaId", candidate.schemaId());
        result.put("schemaVersion", candidate.schemaVersion());
        result.put("topic", candidate.topic());
        result.put("name", candidate.name());
        result.put("modelId", candidate.modelId());
        if (candidate.capabilities() != null && candidate.capabilities().isObject()) {
            var specific = ((com.fasterxml.jackson.databind.node.ObjectNode) candidate.capabilities().deepCopy());
            sharedKeys.forEach(specific::remove);
            specific.remove(List.of("requiredQuantities", "optionalQuantities"));
            specific.remove(List.of("dynamicsLanguage", "composition"));
            result.put("capabilities", specific);
        } else {
            result.put("capabilities", candidate.capabilities());
        }
        result.put("requiredQuantities", candidate.requiredQuantities());
        result.put("optionalQuantities", candidate.optionalQuantities());
        result.put("relationTypes", candidate.relationTypes());
        result.put("endConditionCapabilities", candidate.endConditionCapabilities());
        result.put("entityTypes", candidate.entityTypes());
        result.put("executionDurationSeconds", candidate.executionDurationSeconds());
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
