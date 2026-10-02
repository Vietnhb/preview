package com.example.backend.system.account.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One row of {@code user_permissions}: the user-permission junction with its grant audit. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class PermissionGrant {
    @ManyToOne(optional = false)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;

    /** User ID of the SCHOOL or MANAGER account that granted the permission; null for migrated rows. */
    @Column(name = "granted_by")
    private Integer grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    public PermissionGrant(Permission permission, Integer grantedBy, Instant grantedAt) {
        this.permission = permission;
        this.grantedBy = grantedBy;
        this.grantedAt = grantedAt;
    }

    public String code() {
        return permission == null ? null : permission.getCode();
    }

    /** A user holds a permission at most once, so identity is the permission code. */
    @Override
    public boolean equals(Object other) {
        return other instanceof PermissionGrant grant && Objects.equals(code(), grant.code());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code());
    }
}
