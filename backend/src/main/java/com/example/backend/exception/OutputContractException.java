package com.example.backend.exception;

/** A solver output did not satisfy its schema-pinned output contract. */
public final class OutputContractException extends IllegalArgumentException {
    public OutputContractException(String message) {
        super(message);
    }

    public OutputContractException(String message, Throwable cause) {
        super(message, cause);
    }
}
