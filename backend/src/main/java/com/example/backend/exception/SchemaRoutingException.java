package com.example.backend.exception;

/** No eligible schema candidate could be produced for the supplied problem. */
public final class SchemaRoutingException extends IllegalArgumentException {
    public SchemaRoutingException(String message) {
        super(message);
    }
}
