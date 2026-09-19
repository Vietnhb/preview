package com.example.backend.entity;
import com.example.backend.enums.AmbiguityStatus;

import java.time.Instant;

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
}
