package com.example.backend.schema.routing.vector;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import org.junit.jupiter.api.Test;

class OpenRouterEmbeddingClientTest {
    @Test
    void rejectsProviderLabelsWithoutAnImplementation() {
        assertDoesNotThrow(() -> OpenRouterEmbeddingClient.requireSupportedProvider("openrouter"));
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> OpenRouterEmbeddingClient.requireSupportedProvider("other-provider"));

        assertTrue(failure.getMessage().contains("not implemented"));
    }

    @Test
    void retriesRateLimitAndServerFailuresButNotMalformedOrOtherClientFailures() {
        assertTrue(OpenRouterEmbeddingClient.isRetryable(
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "rate limited", null, null, null)));
        assertTrue(OpenRouterEmbeddingClient.isRetryable(
                HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "gateway", null, null, null)));
        assertTrue(!OpenRouterEmbeddingClient.isRetryable(
                HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "bad request", null, null, null)));
        assertTrue(!OpenRouterEmbeddingClient.isRetryable(
                new IllegalStateException("malformed provider payload")));
    }

    @Test
    void honorsBoundedRetryAfterHeaderAndUsesConfiguredBackoffWhenHeaderIsInvalid() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "2");
        var rateLimited = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "rate limited", headers, null, null);
        assertTrue(OpenRouterEmbeddingClient.retryAfterDelay(rateLimited, Duration.ofMillis(100), 1)
                .equals(Duration.ofSeconds(2)));

        headers.set("Retry-After", "not-a-duration");
        var malformedHeader = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "rate limited", headers, null, null);
        assertTrue(OpenRouterEmbeddingClient.retryAfterDelay(malformedHeader, Duration.ofMillis(100), 2)
                .equals(Duration.ofMillis(200)));
    }
}
