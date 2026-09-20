package com.example.backend.ai.extraction.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/** Parses provider JSON while preserving duplicate-key evidence for validation. */
public final class StrictJsonParser {
    private static final int MAX_RESPONSE_CHARACTERS = 262_144;
    private static final int MAX_NESTING_DEPTH = 64;

    private StrictJsonParser() { }

    public static JsonNode parse(ObjectMapper mapper, String content) throws JsonProcessingException {
        if (mapper == null) throw new IllegalArgumentException("ObjectMapper is required");
        if (content == null || content.isBlank()) throw new IllegalArgumentException("JSON content is required");
        String normalized = content.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        if (normalized.length() > MAX_RESPONSE_CHARACTERS
                || normalized.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_CHARACTERS) {
            throw new IllegalArgumentException("AI response exceeds the strict JSON size limit.");
        }
        requireBoundedDepth(normalized);
        return mapper.reader()
                .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .readTree(normalized);
    }

    private static void requireBoundedDepth(String json) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < json.length(); index++) {
            char current = json.charAt(index);
            if (inString) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') inString = false;
                continue;
            }
            if (current == '"') {
                inString = true;
            } else if (current == '{' || current == '[') {
                if (++depth > MAX_NESTING_DEPTH) {
                    throw new IllegalArgumentException("AI response exceeds the strict JSON nesting limit.");
                }
            } else if (current == '}' || current == ']') {
                depth--;
            }
        }
    }
}
