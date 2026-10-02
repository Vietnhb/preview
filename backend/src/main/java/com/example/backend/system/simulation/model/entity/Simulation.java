package com.example.backend.system.simulation.model.entity;

import com.example.backend.base.crud.model.entity.AuditedEntity;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.problem.model.entity.Specification;
import com.example.backend.system.simulation.model.enums.SimulationStatus;
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
@Table(name = "simulations")
@Getter
@Setter
public class Simulation extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specification_id", nullable = false)
    private Specification specification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, length = 80)
    private String schemaId;

    @Column(nullable = false, length = 120)
    private String solverVersion;

    @Column(nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private SimulationStatus status = SimulationStatus.VALIDATING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "latest_run_id")
    private SimulationRun latestRun;
}
