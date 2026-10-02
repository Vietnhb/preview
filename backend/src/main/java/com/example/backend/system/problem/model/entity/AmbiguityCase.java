package com.example.backend.system.problem.model.entity;

import com.example.backend.base.crud.model.entity.AuditedEntity;
import com.example.backend.system.problem.model.enums.AmbiguityStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ambiguity_cases")
@Getter
@Setter
public class AmbiguityCase extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specification_id", nullable = false)
    private Specification specification;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(nullable = false, length = 160)
    private String fieldPath;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode options;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AmbiguityStatus status;

    @Column(columnDefinition = "text")
    private String resolution;

    private Instant resolvedAt;

    @Column(name = "claimed_by")
    private Integer claimedBy;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "claim_expires_at")
    private Instant claimExpiresAt;

    @Version
    @Column(nullable = false)
    private long version;
}
