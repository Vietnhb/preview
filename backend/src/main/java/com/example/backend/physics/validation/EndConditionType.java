package com.example.backend.physics.validation;

import java.util.Arrays;
import java.util.Locale;

/** Closed set of end-condition contracts understood by the runtime. */
public enum EndConditionType {
    TIME_LIMIT("time_limit"),
    THRESHOLD("threshold"),
    EVENT("event"),
    CYCLE_COUNT("cycle_count"),
    MANUAL("manual");

    private final String wireName;

    EndConditionType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static EndConditionType fromWireName(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(type -> type.wireName.equals(normalized)).findFirst().orElse(null);
    }
}
