package com.example.backend.system.problem.dto;

import java.util.List;
import java.util.UUID;

public record ValidationReadinessResponse(UUID specificationId, boolean ready, List<String> blockers) {
}
