package com.example.backend.dto.physics;

import java.util.List;
import java.util.UUID;

public record ValidationResponse(
        UUID validationRunId,
        boolean passed,
        String schemaId,
        double tolerance,
        List<ValidationCheckpointResponse> checkpoints,
        List<String> errors,
        double validationTimeMs) {
}
