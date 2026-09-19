package com.example.backend.dto.simulation;

public record ValidationCheckpointResponse(
        double time,
        String quantity,
        double numerical,
        double analytical,
        double relativeError,
        double tolerance,
        boolean passed) {
}
