package com.example.backend.entity.evaluation;

import com.example.backend.entity.common.AuditedEntity;

import com.fasterxml.jackson.databind.JsonNode;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "benchmark_adjudications")
@Getter
@Setter
public class Adjudication extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "benchmark_problem_id", nullable = false)
    private BenchmarkProblem benchmarkProblem;

    @Column(nullable = false, length = 80)
    private String reviewerReference;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode resolvedSpecification;

    @Column(nullable = false, length = 24)
    private String disagreementState;

    @Column(columnDefinition = "text")
    private String rationale;
}
