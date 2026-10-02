package com.example.backend.system.account.model.enums;

import java.util.Arrays;
import java.util.Optional;

/** Roles supported by the authorization and school-membership model. */
public enum RoleName {
    ADMIN(1, Scope.PLATFORM),
    MANAGER(2, Scope.PLATFORM),
    REVIEWER(3, Scope.PLATFORM),
    SCHOOL(4, Scope.SCHOOL),
    STAFF(5, Scope.SCHOOL),
    STUDENT(6, Scope.SCHOOL);

    private final int id;
    private final Scope scope;

    RoleName(int id, Scope scope) {
        this.id = id;
        this.scope = scope;
    }

    public int id() { return id; }

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
