package com.example.backend.exception;

/** A pinned schema's numerical/reference binding is missing or inconsistent. */
public final class SolverBindingException extends IllegalStateException {
    public SolverBindingException(String message) {
        super(message);
    }

    public SolverBindingException(String message, Throwable cause) {
        super(message, cause);
    }
}
