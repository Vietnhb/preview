package com.example.backend.entity.school;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * School class (e.g., "LÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºp 10A1", "LÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºp 11 LÃƒÆ’Ã‚Â½ 2").
 * Created by SCHOOL_MANAGER, assigned to teachers.
 * 
 * Business Rules:
 * - SCHOOL_MANAGER creates class and assigns students initially
 * - SCHOOL_MANAGER assigns teachers to classes
 * - 1 teacher can manage multiple classes
 * - Students are added via ClassEnrollment (1 student = 1 class per school year)
 */
@Entity
@Table(name = "school_classes")
@Data
public class SchoolClass {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "school_id", nullable = false)
    private School school;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Grade level: 10, 11, 12
     */
    @Column(name = "grade_level", nullable = false)
    private Integer gradeLevel;

    /**
     * Academic year: "2024-2025", "2025-2026"
     */
    @Column(name = "school_year", nullable = false, length = 20)
    private String schoolYear;

    /**
     * Subject specialization (optional): "LÃƒÆ’Ã‚Â½", "HÃƒÆ’Ã‚Â³a", "Sinh", "ToÃƒÆ’Ã‚Â¡n", etc.
     */
    @Column(length = 50)
    private String subject;

    /**
     * Class active flag.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
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
