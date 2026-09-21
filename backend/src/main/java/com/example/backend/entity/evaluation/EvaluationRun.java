package com.example.backend.entity.evaluation;

import com.example.backend.entity.common.AuditedEntity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "evaluation_runs")
@Getter
@Setter
public class EvaluationRun extends AuditedEntity {
    @Column(nullable = false, length = 80)
    private String evaluationType;

    @Column(nullable = false)
    private int benchmarkCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode metrics;

    @Column(columnDefinition = "text")
    private String report;

    @Column(nullable = false, length = 16)
    private String status = "COMPLETED";

    @Column(name = "requested_by_reference", length = 80)
    private String requestedByReference;

    @Column(name = "started_at")
    private java.time.Instant startedAt;

    @Column(name = "completed_at")
    private java.time.Instant completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "benchmark_snapshot_hash", length = 64)
    private String benchmarkSnapshotHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode configuration;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "failure_message", columnDefinition = "text")
    private String failureMessage;

    @Version
    @Column(nullable = false)
    private long version;
}
