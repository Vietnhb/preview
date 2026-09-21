package com.example.backend.entity.simulation;

import com.example.backend.entity.common.AuditedEntity;

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

    /**
     * Immutable execution identity captured with the run. These columns are
     * nullable only for pre-migration historical rows; newly persisted runs
     * are populated from the pinned compiled schema and binding.
     */
    @Column(name = "schema_id", length = 80)
    private String schemaId;

    @Column(name = "schema_version", length = 24)
    private String schemaVersion;

    @Column(name = "binding_version", length = 24)
    private String bindingVersion;

    @Column(name = "numerical_solver_id", length = 120)
    private String numericalSolverId;

    @Column(name = "reference_solver_id", length = 120)
    private String referenceSolverId;

    @Column(name = "output_contract_version", length = 40)
    private String outputContractVersion;

    @Column(name = "output_contract_checksum", length = 64)
    private String outputContractChecksum;

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
