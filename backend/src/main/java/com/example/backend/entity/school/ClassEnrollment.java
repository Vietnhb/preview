package com.example.backend.entity.school;

import com.example.backend.entity.account.User;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Student enrollment in a class.
 * SCHOOL_MANAGER creates class with students already assigned.
 * 
 * Business Rules:
 * - 1 student can only be in 1 ACTIVE class per school year (UNIQUE constraint)
 * - Student can transfer classes: old enrollment ÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢ TRANSFERRED, new ÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢ ACTIVE
 */
@Entity
@Table(name = "class_enrollments")
@Data
public class ClassEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    /**
     * School year: must match class's school_year.
     * Used in UNIQUE constraint to allow student in different classes across years.
     */
    @Column(name = "school_year", nullable = false, length = 20)
    private String schoolYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentStatus status = EnrollmentStatus.ACTIVE;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum EnrollmentStatus {
        ACTIVE,       // Currently enrolled
        TRANSFERRED,  // Moved to another class
        DROPPED,      // Left school
        COMPLETED     // Graduated / finished year
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (enrolledAt == null) {
            enrolledAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
