package com.example.backend.ai.extraction.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class StrictJsonParserTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesJsonAndOptionalMarkdownFence() throws Exception {
        assertEquals(1, StrictJsonParser.parse(mapper, "```json\n{\"count\":1}\n```").path("count").asInt());
    }

    @Test
    void rejectsDuplicateKeysBeforeTreeBinding() {
        assertThrows(Exception.class, () -> StrictJsonParser.parse(mapper, "{\"schemaId\":\"safe\",\"schemaId\":\"other\"}"));
    }

    @Test
    void rejectsTrailingJsonValuesAndNonJsonText() {
        assertThrows(Exception.class, () -> StrictJsonParser.parse(mapper, "{\"count\":1} {\"count\":2}"));
        assertThrows(Exception.class, () -> StrictJsonParser.parse(mapper, "{\"count\":1} trailing"));
    }

    @Test
    void rejectsOversizedAndDeepResponsesBeforeBuildingJsonTree() {
        String oversized = "{\"value\":\"" + "x".repeat(262_145) + "\"}";
        String deeplyNested = "{".repeat(65) + "0" + "}".repeat(65);

        assertThrows(IllegalArgumentException.class, () -> StrictJsonParser.parse(mapper, oversized));
        assertThrows(IllegalArgumentException.class, () -> StrictJsonParser.parse(mapper, deeplyNested));
    }
}
