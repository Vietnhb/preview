package com.example.backend.config.database;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseSchemaModeValidatorTest {
    @Test
    void flywayManagedStartupRequiresHibernateValidation() {
        assertDoesNotThrow(() -> DatabaseSchemaModeValidator.validate("validate", true));
        assertThrows(IllegalStateException.class,
                () -> DatabaseSchemaModeValidator.validate("update", true));
        assertThrows(IllegalStateException.class,
                () -> DatabaseSchemaModeValidator.validate("create-drop", true));
    }

    @Test
    void hibernateBootstrapModesRemainAvailableOnlyWhenFlywayIsDisabled() {
        assertDoesNotThrow(() -> DatabaseSchemaModeValidator.validate("update", false));
        assertDoesNotThrow(() -> DatabaseSchemaModeValidator.validate("create-drop", false));
    }

    @Test
    void missingModeFailsClosed() {
        assertThrows(IllegalStateException.class,
                () -> DatabaseSchemaModeValidator.validate(" ", false));
    }
}
