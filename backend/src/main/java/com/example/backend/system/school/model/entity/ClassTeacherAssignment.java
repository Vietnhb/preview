package com.example.backend.system.school.model.entity;

import com.example.backend.system.account.model.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/**
 * Many-to-Many: Teacher <-> Class assignment.
 * SCHOOL assigns teachers to classes.
 * 1 teacher can teach multiple classes.
 */
@Entity
@Table(
    name = "class_teacher_assignments",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "unique_teacher_class",
            columnNames = {"class_id", "teacher_id"}
        )
    }
)
@Data
public class ClassTeacherAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private SchoolClass schoolClass;

    @ManyToOne(optional = false)
    @JoinColumn(name = "teacher_id", nullable = false)
    private User teacher;

    /**
     * Assignment active flag.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @PrePersist
    protected void onCreate() {
        if (assignedAt == null) {
            assignedAt = Instant.now();
        }
    }
}
