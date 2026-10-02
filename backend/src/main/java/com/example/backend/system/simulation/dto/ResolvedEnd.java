package com.example.backend.system.simulation.dto;

/** Authoritative end of the current solver run. */
public record ResolvedEnd(
        double time,
        String reason,
        boolean conditionReached) {
}
