package com.example.backend.config.database;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Prevents a normal Flyway-managed startup from silently mutating the schema
 * through Hibernate. The documented empty-database bootstrap is the only
 * supported path with Flyway disabled and Hibernate schema creation enabled.
 */
@Component
public final class DatabaseSchemaModeValidator {
    DatabaseSchemaModeValidator(
            @Value("${spring.jpa.hibernate.ddl-auto}") String ddlAuto,
            @Value("${spring.flyway.enabled}") boolean flywayEnabled) {
        validate(ddlAuto, flywayEnabled);
    }

    static void validate(String ddlAuto, boolean flywayEnabled) {
        String mode = ddlAuto == null ? "" : ddlAuto.trim().toLowerCase(java.util.Locale.ROOT);
        if (mode.isBlank()) {
            throw new IllegalStateException("spring.jpa.hibernate.ddl-auto must be configured explicitly");
        }
        if (flywayEnabled && !"validate".equals(mode)) {
            throw new IllegalStateException(
                    "Flyway-managed startup requires spring.jpa.hibernate.ddl-auto=validate; "
                            + "use the documented empty-database bootstrap before enabling Flyway");
        }
    }
}
