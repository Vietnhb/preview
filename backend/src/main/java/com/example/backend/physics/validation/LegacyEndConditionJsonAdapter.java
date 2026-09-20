package com.example.backend.physics.validation;

import com.fasterxml.jackson.databind.JsonNode;

/** @deprecated Use the explicit versioned V1 adapter for historical JSON payloads. */
@Deprecated(forRemoval = false)
public final class LegacyEndConditionJsonAdapter {
    private LegacyEndConditionJsonAdapter() { }

    /** Retained as a source-compatible facade for existing callers and replay tests. */
    public static EndConditionContract compile(JsonNode condition, double fallbackDuration) {
        return LegacyEndConditionJsonAdapterV1.compile(condition, fallbackDuration);
    }
}
