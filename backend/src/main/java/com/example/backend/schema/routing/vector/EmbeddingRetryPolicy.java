package com.example.backend.schema.routing.vector;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Bounded retry policy for transient embedding transport failures.
 * It never retries malformed provider data or contract/configuration errors.
 */
public final class EmbeddingRetryPolicy {
    private final int maxAttempts;
    private final Duration retryBackoff;
    private final Sleeper sleeper;

    public EmbeddingRetryPolicy(int maxAttempts, Duration retryBackoff) {
        this(maxAttempts, retryBackoff, EmbeddingRetryPolicy::sleep);
    }

    EmbeddingRetryPolicy(int maxAttempts, Duration retryBackoff, Sleeper sleeper) {
        if (maxAttempts < 1 || maxAttempts > 5) throw new IllegalArgumentException("maxAttempts must be in [1, 5]");
        this.maxAttempts = maxAttempts;
        this.retryBackoff = Objects.requireNonNull(retryBackoff, "retryBackoff");
        if (retryBackoff.isNegative() || retryBackoff.compareTo(Duration.ofSeconds(5)) > 0) {
            throw new IllegalArgumentException("retryBackoff must be in [0, 5s]");
        }
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    public <T> T execute(Callable<T> operation, Predicate<RuntimeException> retryable) {
        return execute(operation, retryable, (ignored, attempt) -> retryBackoff.multipliedBy(attempt));
    }

    public <T> T execute(Callable<T> operation, Predicate<RuntimeException> retryable,
                         BiFunction<RuntimeException, Integer, Duration> delayFor) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(retryable, "retryable");
        Objects.requireNonNull(delayFor, "delayFor");
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.call();
            } catch (RuntimeException failure) {
                lastFailure = failure;
                if (attempt == maxAttempts || !retryable.test(failure)) throw failure;
                Duration delay = Objects.requireNonNull(delayFor.apply(failure, attempt), "retry delay");
                if (delay.isNegative()) throw new IllegalArgumentException("retry delay must not be negative");
                sleeper.pause(delay);
            } catch (Exception failure) {
                throw new IllegalStateException("Embedding request operation failed", failure);
            }
        }
        throw new IllegalStateException("Embedding retry policy ended without a result", lastFailure);
    }

    private static void sleep(Duration delay) {
        if (delay.isZero()) return;
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Embedding retry interrupted", interrupted);
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void pause(Duration delay);
    }
}
