package com.example.backend.system.reviewer.dto;

import com.example.backend.system.reviewer.model.entity.EvaluationRun;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read and compare persisted evaluation reports. */
public final class EvaluationContracts {
    private EvaluationContracts() { }
    public record RunView(UUID id, String evaluationType, String status, int benchmarkCount,
                          JsonNode metrics, String report, String requestedByReference,
                          Instant createdAt, Instant startedAt, Instant completedAt,
                          Long durationMs, String benchmarkSnapshotHash, JsonNode configuration,
                          String failureCode, String failureMessage) {
        public static RunView from(EvaluationRun run) {
            return new RunView(run.getId(), run.getEvaluationType(), run.getStatus(), run.getBenchmarkCount(),
                    run.getMetrics(), run.getReport(), run.getRequestedByReference(), run.getCreatedAt(),
                    run.getStartedAt(), run.getCompletedAt(), run.getDurationMs(), run.getBenchmarkSnapshotHash(),
                    run.getConfiguration(), run.getFailureCode(), run.getFailureMessage());
        }
    }
    public record RunPage(List<RunView> items, int page, int size, long totalElements, int totalPages) { }
    public record RunComparison(RunView baseline, RunView candidate, JsonNode metricDelta) { }
}
