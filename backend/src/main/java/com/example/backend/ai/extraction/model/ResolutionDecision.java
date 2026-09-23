package com.example.backend.ai.extraction.model;

import java.util.List;

/** Model classification of how a specific user answer resolves an open ambiguity. */
public record ResolutionDecision(String code, Outcome outcome, List<String> omittedObjectIds) {
    public ResolutionDecision {
        omittedObjectIds = omittedObjectIds == null ? List.of() : List.copyOf(omittedObjectIds);
    }

    public enum Outcome {
        ANSWERED,
        ACCEPT_SIMPLIFICATION,
        DECLINE_SIMPLIFICATION,
        REVISE_REQUEST,
        START_NEW_PROBLEM,
        UNRESOLVED
    }
}
