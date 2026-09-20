package com.example.backend.exception;

/** Invalid or internally inconsistent compiled schema data. This is a server configuration failure. */
public final class SchemaCompilationException extends IllegalArgumentException {
    public SchemaCompilationException(String message) {
        super(message);
    }

    public SchemaCompilationException(String message, Throwable cause) {
        super(message, cause);
    }
}
