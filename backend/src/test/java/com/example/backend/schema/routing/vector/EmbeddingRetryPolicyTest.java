package com.example.backend.schema.routing.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class EmbeddingRetryPolicyTest {
    @Test
    void retriesOnlyTransientFailuresAndStopsAfterSuccess() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger pauses = new AtomicInteger();
        EmbeddingRetryPolicy policy = new EmbeddingRetryPolicy(3, Duration.ofMillis(1), ignored -> pauses.incrementAndGet());

        String result = policy.execute(() -> {
            if (attempts.incrementAndGet() < 3) throw new IllegalStateException("transient");
            return "ok";
        }, failure -> true);

        assertEquals("ok", result);
        assertEquals(3, attempts.get());
        assertEquals(2, pauses.get());
    }

    @Test
    void doesNotRetryNonTransientFailure() {
        AtomicInteger attempts = new AtomicInteger();
        EmbeddingRetryPolicy policy = new EmbeddingRetryPolicy(5, Duration.ZERO, ignored -> {
            throw new AssertionError("non-transient failure must not sleep");
        });

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> policy.execute(() -> {
                    attempts.incrementAndGet();
                    throw new IllegalArgumentException("malformed provider payload");
                }, ignored -> false));

        assertTrue(failure.getMessage().contains("malformed"));
        assertEquals(1, attempts.get());
    }

    @Test
    void enforcesAttemptBoundForPersistentTransientFailure() {
        AtomicInteger attempts = new AtomicInteger();
        EmbeddingRetryPolicy policy = new EmbeddingRetryPolicy(2, Duration.ZERO, ignored -> { });

        assertThrows(IllegalStateException.class, () -> policy.execute(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("provider unavailable");
        }, ignored -> true));

        assertEquals(2, attempts.get());
    }
}
