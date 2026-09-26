package com.example.backend.entity.problem;

import com.example.backend.entity.common.AuditedEntity;
import com.example.backend.entity.enums.ConfirmationState;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "specifications")
@Getter
@Setter
public class Specification extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private ProblemSubmission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "extraction_run_id", nullable = false)
    private ExtractionRun extractionRun;

    @Column(nullable = false, length = 16)
    private String schemaVersion;

    @Column(name = "contract_version", length = 16)
    private String contractVersion;

    @Column(length = 32)
    private String topic;

    @Column(name = "schema_id", length = 80)
    private String schemaId;

    @Column(name = "validation_status", length = 24)
    private String validationStatus = "NOT_VALIDATED";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_result", columnDefinition = "jsonb")
    private JsonNode validationResult;

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode objects;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode quantities;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode relations;

    /** Declarative termination intent. Null is intentionally supported for legacy rows. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "end_condition", columnDefinition = "jsonb")
    private JsonNode endCondition;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode ambiguity;

    /** Accepted clarification turns, preserved independently of the browser session. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "clarification_conversation", columnDefinition = "jsonb")
    private JsonNode clarificationConversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ConfirmationState confirmationState;

    @OneToMany(mappedBy = "specification", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<AmbiguityCase> ambiguityCases = new ArrayList<>();

    public void addAmbiguityCase(AmbiguityCase ambiguityCase) {
        ambiguityCases.add(ambiguityCase);
        ambiguityCase.setSpecification(this);
    }
}
