package com.example.backend.system.account.model.entity;

import com.example.backend.system.school.model.entity.School;
import com.example.backend.system.account.model.enums.PermissionCode;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

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
     * Business Rules (enforced via database constraint in the database bootstrap SQL):
     * - Platform roles (ADMIN, MANAGER, REVIEWER): school_id MUST be NULL
     * - School roles (SCHOOL, STAFF, STUDENT): school_id MUST be NOT NULL
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

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    /**
     * Permissions granted to this account (N-N through user_permissions).
     * STAFF: TEACH and/or DEPARTMENT_HEAD_PHYSICS, granted by the school. REVIEWER: CONTENT_EDIT and/or
     * CONTENT_REVIEW, granted by a manager. Other roles hold none.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permissions", joinColumns = @JoinColumn(name = "user_id"))
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<PermissionGrant> permissions = new LinkedHashSet<>();

    public boolean hasPermission(PermissionCode code) {
        return permissions.stream().anyMatch(grant -> code.name().equals(grant.code()));
    }

    /** Permission codes in catalog order. */
    public List<String> permissionCodes() {
        return Arrays.stream(PermissionCode.values()).filter(this::hasPermission).map(Enum::name).toList();
    }

    public void grantPermission(Permission permission, Integer grantedBy) {
        // Millisecond precision keeps the stored timestamp identical to the in-memory value.
        permissions.add(new PermissionGrant(permission, grantedBy, Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)));
    }
}
