package com.example.backend.dto.problem;

import java.util.List;
import java.util.UUID;

public record ValidationReadinessResponse(UUID specificationId, boolean ready, List<String> blockers) {
}
