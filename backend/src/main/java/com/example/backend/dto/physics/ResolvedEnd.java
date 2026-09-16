package com.example.backend.dto.physics;

/** Authoritative end of the current solver run. */
public record ResolvedEnd(
        double time,
        String reason,
        boolean conditionReached) {
}
