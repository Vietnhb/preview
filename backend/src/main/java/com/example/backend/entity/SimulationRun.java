package com.example.backend.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "simulation_runs")
@Getter
@Setter
public class SimulationRun extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "simulation_id", nullable = false)
    private Simulation simulation;

    @Column(nullable = false, length = 24)
    private String runType;

    @Column(nullable = false)
    private boolean validationPassed;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_checkpoints", nullable = false, columnDefinition = "jsonb")
    private JsonNode validationCheckpoints;

    @Column(name = "validation_error", columnDefinition = "text")
    private String validationError;

    @Column(nullable = false)
    private double durationSeconds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode result;
}
