package com.example.backend.physics.validation;

import java.util.Locale;
import java.util.Objects;

/** Compiled binding to a declared time-series output. AUTO exists only for legacy payloads. */
public record OutputSourceBinding(Group group, String key) {
    public enum Group {
        VALUES, POSITIONS, VELOCITIES, ACCELERATIONS, LEGACY_AUTO, LEGACY_ENTITY_POSITION
    }

    public OutputSourceBinding {
        Objects.requireNonNull(group, "group");
        if (key == null || key.isBlank()) throw new IllegalArgumentException("Output source key is required");
        key = key.trim();
    }

    public static OutputSourceBinding declared(Group group, String key) {
        if (group == Group.LEGACY_AUTO || group == Group.LEGACY_ENTITY_POSITION)
            throw new IllegalArgumentException("Legacy source groups are reserved for compatibility");
        return new OutputSourceBinding(group, key);
    }

    /** Parses the pre-compiled quantity string contract at the explicit legacy boundary. */
    static OutputSourceBinding fromLegacy(String quantity) {
        if (quantity == null || quantity.isBlank()) throw new IllegalArgumentException("Output source is required");
        String raw = quantity.trim();
        int dot = raw.lastIndexOf('.');
        if (dot > 0) {
            String group = raw.substring(0, dot).toLowerCase(Locale.ROOT);
            Group typed = switch (group) {
                case "value", "values" -> Group.VALUES;
                case "position", "positions" -> Group.POSITIONS;
                case "velocity", "velocities" -> Group.VELOCITIES;
                case "acceleration", "accelerations" -> Group.ACCELERATIONS;
                default -> null;
            };
            if (typed != null) return new OutputSourceBinding(typed, raw.substring(dot + 1));
        }
        return new OutputSourceBinding(Group.LEGACY_AUTO, raw);
    }
}
