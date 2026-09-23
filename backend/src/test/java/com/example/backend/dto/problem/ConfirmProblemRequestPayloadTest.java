package com.example.backend.dto.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ConfirmProblemRequestPayloadTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exactBackendAmbiguityCodeIsParsedWithoutBackslash() throws Exception {
        String json = "{\"answers\":{\"schema.required.initial_position\":\"0\"},\"conversation\":[]}";

        JsonNode request = mapper.readTree(json);
        String key = request.path("answers").fieldNames().next();

        assertEquals("schema.required.initial_position", key);
    }

    @Test
    void escapedBackslashProducesDifferentAnswerKey() throws Exception {
        String json = "{\"answers\":{\"schema.required.initial\\\\_position\":\"0\"},\"conversation\":[]}";

        JsonNode request = mapper.readTree(json);
        String key = request.path("answers").fieldNames().next();

        assertEquals("schema.required.initial\\_position", key);
        assertFalse(key.equals("schema.required.initial_position"));
    }

    @Test
    void singleJsonBackslashBeforeUnderscoreIsInvalidJson() {
        String invalidJson = "{\"answers\":{\"schema.required.initial\\_position\":\"0\"},\"conversation\":[]}";

        assertThrows(Exception.class, () -> mapper.readTree(invalidJson));
    }
}
