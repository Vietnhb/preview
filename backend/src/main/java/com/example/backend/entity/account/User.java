package com.example.backend.entity.account;

import com.example.backend.entity.school.School;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Data;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(nullable = false)
    private String password;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;

    // Nullable keeps existing rows compatible during the first schema upgrade.
    @Column(nullable = true)
    private Boolean active = true;

    /**
     * School the user belongs to (nullable for platform roles).
     *
     * Business Rules (enforced via database constraint in data.sql):
     * - Platform roles (ADMIN, REVIEWER): school_id MUST be NULL
     * - School roles (SCHOOL_MANAGER, TEACHER, STUDENT): school_id MUST be NOT NULL
     */
    @ManyToOne
    @JoinColumn(name = "school_id")
    private School school;

    /** Legacy API field derived from the school relationship. */
    public String getInstitutionId() {
        return school == null ? null : school.getId().toString();
    }

    /**
     * Soft delete tracking: who deactivated this user (User ID).
     */
    @Column(name = "deactivated_by")
    private Integer deactivatedBy;

    /**
     * Soft delete tracking: reason for deactivation.
     */
    @Column(name = "deactivation_reason", columnDefinition = "TEXT")
    private String deactivationReason;

    /**
     * Soft delete tracking: timestamp of deactivation.
     */
    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @Column(name = "last_login")
    private Instant lastLogin;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;
}
