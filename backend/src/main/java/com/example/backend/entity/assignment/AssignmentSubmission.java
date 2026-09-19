package com.example.backend.entity.assignment;

import com.example.backend.entity.account.User;
import com.example.backend.entity.common.AuditedEntity;
import com.example.backend.entity.enums.GradingStatus;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "assignment_submissions", uniqueConstraints =
        @UniqueConstraint(name = "uk_assignment_submission_student", columnNames = {"assignment_id", "student_id"}))
@Getter
@Setter
public class AssignmentSubmission extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode predictions;

    @Column(nullable = false)
    private Instant submittedAt;

    /** Time the student finished the experiment and explicitly handed in the work. */
    @Column
    private Instant completedAt;

    @Column(precision = 8, scale = 3)
    private java.math.BigDecimal score;

    @Column(precision = 8, scale = 3)
    private java.math.BigDecimal maxScore;

    @Column(columnDefinition = "text")
    private String feedback;

    @Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 24)
    private GradingStatus gradingStatus = GradingStatus.PENDING;

    @Column
    private Instant gradedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by")
    private User gradedBy;

    @Column(nullable = false)
    private boolean retryAllowed;
}
