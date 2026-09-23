package com.example.backend.ai.extraction;

/** Carries the failed AI pipeline stage without exposing provider internals to users. */
public final class AiStepException extends RuntimeException {
    private final String step;
    private final String code;

    public AiStepException(String step, String code, String message, Throwable cause) {
        super(message, cause);
        this.step = step;
        this.code = code;
    }

    public String step() { return step; }
    public String code() { return code; }
}
