package com.example.backend.system.account.model.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Permissions granted to individual accounts through {@code user_permissions}.
 * Each permission belongs to exactly one role; an account may hold several permissions of its role.
 */
public enum PermissionCode {
    TEACH(RoleName.STAFF, "Giáo viên"),
    DEPARTMENT_HEAD_PHYSICS(RoleName.STAFF, "Tổ trưởng bộ môn Vật Lý"),
    CONTENT_EDIT(RoleName.REVIEWER, "Biên soạn"),
    CONTENT_REVIEW(RoleName.REVIEWER, "Kiểm duyệt");

    private final RoleName role;
    private final String label;

    PermissionCode(RoleName role, String label) {
        this.role = role;
        this.label = label;
    }

    public RoleName role() { return role; }

    public String label() { return label; }

    public static Optional<PermissionCode> from(String value) {
        if (value == null) return Optional.empty();
        String code = value.trim();
        return Arrays.stream(values()).filter(item -> item.name().equalsIgnoreCase(code)).findFirst();
    }

    public static List<PermissionCode> forRole(RoleName role) {
        return Arrays.stream(values()).filter(item -> item.role == role).toList();
    }

    /** Permissions a new account of the role receives when the grantor does not choose. */
    public static List<PermissionCode> defaultsFor(RoleName role) {
        return role == RoleName.STAFF ? List.of(TEACH) : forRole(role);
    }
}
