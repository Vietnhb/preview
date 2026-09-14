package com.example.backend.entity;

import com.fasterxml.jackson.databind.JsonNode;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "extraction_runs")
@Getter
@Setter
public class ExtractionRun extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private ProblemSubmission submission;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExtractionPath extractionPath;

    @Column(nullable = false, length = 120)
    private String providerName;

    @Column(length = 120)
    private String modelVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ExtractionRunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private ExtractionOutcome outcome;

    @Column(columnDefinition = "text")
    private String errorMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode rawResponse;
}
