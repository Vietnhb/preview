package com.example.backend.entity.simulation;

import com.example.backend.entity.common.AuditedEntity;
import com.example.backend.entity.enums.LifecycleStatus;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "solver_versions", uniqueConstraints =
        @UniqueConstraint(name = "uk_solver_versions_identity", columnNames = {"schema_id", "version"}))
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

    @Column(name = "binding_checksum", length = 64)
    private String bindingChecksum;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LifecycleStatus lifecycleStatus = LifecycleStatus.DRAFT;
}
