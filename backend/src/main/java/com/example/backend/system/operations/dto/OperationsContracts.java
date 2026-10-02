package com.example.backend.system.operations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Contracts for platform operations managed alongside subscription plans. */
public final class OperationsContracts {
    private OperationsContracts() { }

    public record PlanRequest(@NotBlank @Size(max = 40) @Pattern(regexp = "[A-Z0-9_-]+") String code,
                              @NotBlank @Size(max = 255) String name,
                              @NotBlank @Size(max = 255) String description,
                              @NotNull @Positive Long annualPriceVnd,
                              @NotNull @Positive Integer studentQuota,
                              @PositiveOrZero Integer monthlyTokenQuota, boolean active) { }

    public record TopicStatus(UUID id, String name, boolean enabled) { }

    public record ValidationMetrics(long total, long failed, double failureRate) { }

    public record ValidationRow(UUID id, UUID submissionId, String topic, String schemaId, String schemaVersion,
                                String solverVersion, boolean passed, String status, String errorMessage,
                                Instant createdAt) { }
}
