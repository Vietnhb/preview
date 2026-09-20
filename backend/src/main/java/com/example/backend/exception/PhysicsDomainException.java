package com.example.backend.exception;

/** Input quantities violate a model's physical or numerical domain. */
public final class PhysicsDomainException extends IllegalArgumentException {
    public PhysicsDomainException(String message) {
        super(message);
    }

    public PhysicsDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
