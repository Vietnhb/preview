package com.example.backend.bootstrap;

import com.example.backend.physics.module.PhysicsModuleConfiguration;
import com.example.backend.physics.module.circuits.AcWaveformModule;
import com.example.backend.physics.module.modern.RadioactiveDecayModule;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApprovedLatestPhysicsModuleBindingAuditTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void activeCatalogLatestVersionsHaveTypedModuleBindings() throws Exception {
        JsonNode catalog;
        try (InputStream input = new ClassPathResource("schemas/catalog.json").getInputStream()) {
            catalog = mapper.readTree(input);
        }

        var bindings = ApprovedLatestPhysicsModuleBindingAudit.fromActiveCatalog(catalog);
        assertDoesNotThrow(() -> ApprovedLatestPhysicsModuleBindingAudit.requireTypedBindings(
                bindings, new PhysicsModuleConfiguration().physicsModuleRegistry()));
    }

    @Test
    void rejectsApprovedLatestSchemaWithoutTypedModuleBinding() {
        var binding = new ApprovedLatestPhysicsModuleBindingAudit.CatalogBinding(
                "new_schema", "2.0", "new_schema", "missing_numerical", "missing_reference");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ApprovedLatestPhysicsModuleBindingAudit.requireTypedBindings(
                        List.of(binding), new PhysicsModuleConfiguration().physicsModuleRegistry()));
        assertTrue(failure.getMessage().contains("new_schema@2.0"));
        assertTrue(failure.getMessage().contains("no typed PhysicsModuleRegistry binding"));
    }

    @Test
    void rejectsNumericalAndReferenceIdsOwnedByDifferentTypedModules() {
        var binding = new ApprovedLatestPhysicsModuleBindingAudit.CatalogBinding(
                "bad_pair", "3.1", "bad_pair", AcWaveformModule.NUMERICAL_SOLVER_ID,
                RadioactiveDecayModule.REFERENCE_SOLVER_ID);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ApprovedLatestPhysicsModuleBindingAudit.requireTypedBindings(
                        List.of(binding), new PhysicsModuleConfiguration().physicsModuleRegistry()));
        assertTrue(failure.getMessage().contains("bad_pair@3.1"));
        assertTrue(failure.getMessage().contains("binding identity mismatch"));
    }

    @Test
    void selectsHighestVersionPerSchemaAndRejectsDuplicateCatalogIdentities() {
        var old = new ApprovedLatestPhysicsModuleBindingAudit.CatalogBinding(
                "radioactive_decay", "1.0", "radioactive_decay", "legacy_solver", "legacy_reference");
        var current = new ApprovedLatestPhysicsModuleBindingAudit.CatalogBinding(
                "radioactive_decay", "1.10", "radioactive_decay", RadioactiveDecayModule.NUMERICAL_SOLVER_ID,
                RadioactiveDecayModule.REFERENCE_SOLVER_ID);
        var registry = new PhysicsModuleConfiguration().physicsModuleRegistry();

        assertDoesNotThrow(() -> ApprovedLatestPhysicsModuleBindingAudit.requireTypedBindings(
                List.of(old, current), registry));
        IllegalStateException duplicate = assertThrows(IllegalStateException.class,
                () -> ApprovedLatestPhysicsModuleBindingAudit.requireTypedBindings(
                        List.of(current, current), registry));
        assertTrue(duplicate.getMessage().contains("Duplicate active schema catalog identity"));
    }
}
