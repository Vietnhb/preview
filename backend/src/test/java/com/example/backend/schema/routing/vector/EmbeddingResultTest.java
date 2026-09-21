package com.example.backend.schema.routing.vector;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

class EmbeddingResultTest {
    @Test
    void acceptsFiniteNonZeroVector() {
        assertDoesNotThrow(() -> new EmbeddingResult(List.of(1.0, -2.0)));
    }

    @Test
    void rejectsZeroNormVectorBeforeCosineRetrieval() {
        assertThrows(IllegalArgumentException.class,
                () -> new EmbeddingResult(List.of(0.0, 0.0)));
    }

    @Test
    void rejectsNonFiniteNormEvenWhenComponentsAreIndividuallyFinite() {
        assertThrows(IllegalArgumentException.class,
                () -> new EmbeddingResult(List.of(Double.MAX_VALUE, Double.MAX_VALUE)));
    }
}
