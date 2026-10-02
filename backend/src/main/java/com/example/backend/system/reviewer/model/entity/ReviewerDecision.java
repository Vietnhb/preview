package com.example.backend.system.reviewer.model.entity;

import com.example.backend.base.crud.model.entity.AuditedEntity;
import com.example.backend.system.problem.model.entity.AmbiguityCase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "reviewer_decisions")
@Getter
@Setter
public class ReviewerDecision extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ambiguity_case_id", nullable = false)
    private AmbiguityCase ambiguityCase;

    @Column(nullable = false)
    private Integer actorId;

    @Column(nullable = false, length = 24)
    private String actorRole;

    @Column(nullable = false, length = 24)
    private String decisionState;

    @Column(nullable = false, columnDefinition = "text")
    private String answer;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(nullable = false)
    private Instant decidedAt;
}
