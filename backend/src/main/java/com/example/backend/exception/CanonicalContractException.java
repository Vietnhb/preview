package com.example.backend.exception;

/** A specification quantity violated its pinned schema contract at runtime ingress. */
public final class CanonicalContractException extends RuntimeException {
    public CanonicalContractException(String message) {
        super(message);
    }

    public CanonicalContractException(String message, Throwable cause) {
        super(message, cause);
    }
}
