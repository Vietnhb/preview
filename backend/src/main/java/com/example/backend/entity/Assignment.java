package com.example.backend.entity;
import com.example.backend.enums.AssignmentStatus;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "assignments")
@Getter
@Setter
public class Assignment extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "library_item_id")
    private LibraryItem libraryItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "specification_id", nullable = false)
    private Specification specification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    /** The exact validated teacher run visible to students after the prediction gate. */
    @Column(name = "assigned_simulation_run_id")
    private UUID assignedSimulationRunId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode questions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "grading_criteria", columnDefinition = "jsonb")
    private JsonNode gradingCriteria;

    @Column(name = "max_score", precision = 8, scale = 3, nullable = false)
    private java.math.BigDecimal maxScore = java.math.BigDecimal.TEN;

    @Column(name = "auto_grade", nullable = false)
    private boolean autoGrade;

    @ElementCollection
    @CollectionTable(name = "assignment_students", joinColumns = @JoinColumn(name = "assignment_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_assignment_student", columnNames = {"assignment_id", "student_id"}))
    @Column(name = "student_id", nullable = false)
    private Set<Integer> assignedStudentIds = new LinkedHashSet<>();

    @Column(nullable = false)
    private Instant assignedAt;

    @Column
    private Instant dueAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssignmentStatus status = AssignmentStatus.ACTIVE;
}
