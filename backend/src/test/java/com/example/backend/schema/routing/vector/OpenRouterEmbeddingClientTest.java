package com.example.backend.schema.routing.vector;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OpenRouterEmbeddingClientTest {
    @Test
    void rejectsProviderLabelsWithoutAnImplementation() {
        assertDoesNotThrow(() -> OpenRouterEmbeddingClient.requireSupportedProvider("openrouter"));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> OpenRouterEmbeddingClient.requireSupportedProvider("other-provider"));

        assertTrue(failure.getMessage().contains("not implemented"));
    }
}
