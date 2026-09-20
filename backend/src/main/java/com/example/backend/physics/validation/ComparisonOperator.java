package com.example.backend.physics.validation;

import java.util.Arrays;

/** Allow-listed comparison operators for declarative end conditions. */
public enum ComparisonOperator {
    GREATER_OR_EQUAL(">="), LESS_OR_EQUAL("<="), GREATER(">"), LESS("<"), EQUAL("==");

    private final String token;
    ComparisonOperator(String token) { this.token = token; }
    public String token() { return token; }

    public static ComparisonOperator parse(String raw) {
        if (raw == null) return null;
        return Arrays.stream(values()).filter(value -> value.token.equals(raw.trim())).findFirst().orElse(null);
    }

    public ComparisonOperator boundary() {
        return this == GREATER ? GREATER_OR_EQUAL : this == LESS ? LESS_OR_EQUAL : this;
    }
}
