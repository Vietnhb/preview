package com.example.backend.service.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ConfirmedSpecificationProjectionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void assetProjectionRestoresPersistedNormalizedQuantities() throws Exception {
        var llmProjection = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree("""
                {"quantities":[{"name":"initial_velocity","value":10,"originalUnit":"m/s"}]}
                """);
        var persistedQuantities = mapper.readTree("""
                [{"name":"initial_velocity","value":10,"originalUnit":"m/s",
                  "normalizedValue":10,"normalizedUnit":"m/s"}]
                """);

        var confirmed = AmbiguityResolutionApplier.withPersistedQuantities(llmProjection, persistedQuantities);

        assertEquals(10, confirmed.path("quantities").get(0).path("normalizedValue").asInt());
        assertEquals("m/s", confirmed.path("quantities").get(0).path("normalizedUnit").asText());
        assertFalse(llmProjection.path("quantities").get(0).has("normalizedValue"),
                "The LLM-facing projection remains unchanged.");
    }
}
