package com.example.backend.entity.enums;

import java.util.Arrays;
import java.util.Optional;

/** Roles supported by the authorization and school-membership model. */
public enum RoleName {
    ADMIN(Scope.PLATFORM),
    REVIEWER(Scope.PLATFORM),
    SCHOOL_MANAGER(Scope.SCHOOL),
    TEACHER(Scope.SCHOOL),
    STUDENT(Scope.SCHOOL);

    private final Scope scope;

    RoleName(Scope scope) {
        this.scope = scope;
    }

    public boolean matches(String value) {
        return value != null && name().equalsIgnoreCase(value.trim());
    }

    public boolean isPlatformRole() {
        return scope == Scope.PLATFORM;
    }

    public boolean isSchoolRole() {
        return scope == Scope.SCHOOL;
    }

    public String authority() {
        return "ROLE_" + name();
    }

    public static Optional<RoleName> from(String value) {
        return Arrays.stream(values()).filter(role -> role.matches(value)).findFirst();
    }

    private enum Scope {
        PLATFORM,
        SCHOOL
    }
}
