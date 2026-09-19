package com.example.backend.entity.evaluation;

import com.example.backend.entity.common.AuditedEntity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
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
}
