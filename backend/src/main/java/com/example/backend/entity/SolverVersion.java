package com.example.backend.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "solver_versions")
@Getter
@Setter
public class SolverVersion extends AuditedEntity {
    @Column(name = "schema_id", nullable = false, length = 80)
    private String schemaId;

    @Column(name = "solver_id", nullable = false, length = 120)
    private String solverId;

    @Column(nullable = false, length = 24)
    private String version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode outputDefinition;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LifecycleStatus lifecycleStatus = LifecycleStatus.DRAFT;
}
