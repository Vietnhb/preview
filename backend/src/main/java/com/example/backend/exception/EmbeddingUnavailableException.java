package com.example.backend.exception;

import org.springframework.http.HttpStatus;

/** Safe, typed failure for missing vector infrastructure or an unavailable embedding provider. */
public final class EmbeddingUnavailableException extends ApiException {
    public EmbeddingUnavailableException(String action) {
        super(HttpStatus.SERVICE_UNAVAILABLE,
                "Schema routing is temporarily unavailable. " + action);
    }
}
