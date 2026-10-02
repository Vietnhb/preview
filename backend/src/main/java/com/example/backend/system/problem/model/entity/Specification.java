package com.example.backend.system.problem.model.entity;

import com.example.backend.base.crud.model.entity.AuditedEntity;
import com.example.backend.system.problem.model.enums.ConfirmationState;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
    public java.util.Comparator<AmbiguityCase> questionOrder() {
        java.util.Map<String, Integer> order = new java.util.HashMap<>();
        JsonNode questions = getAmbiguity();
        if (questions != null && questions.isArray())
            for (int i = 0; i < questions.size(); i++) order.put(questions.get(i).path("code").asText(), i);
        return java.util.Comparator.comparingInt(item -> order.getOrDefault(item.getCode(), Integer.MAX_VALUE));
    }
}
