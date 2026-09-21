package com.example.backend.bootstrap;

import com.example.backend.service.problem.SchemaCompiler;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.example.backend.entity.enums.LifecycleStatus;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.entity.simulation.SolverVersion;
import com.example.backend.physics.module.PhysicsModuleConfiguration;
import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;
import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolverRegistry;
import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;
import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolverRegistry;
import com.example.backend.repository.problem.SchemaVersionRepository;
import com.example.backend.repository.simulation.SolverVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchemaCatalogHistoryTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void supersededPublishedVersionsRemainCompilableButSeparateFromCurrentRoutingCatalog() throws Exception {
        JsonNode activeCatalog;
        JsonNode history;
        try (InputStream active = new ClassPathResource("schemas/catalog.json").getInputStream();
             InputStream archived = new ClassPathResource("schemas/history/published-versions.json").getInputStream()) {
            activeCatalog = mapper.readTree(active);
            history = mapper.readTree(archived);
        }

        Set<String> activeIdentities = new HashSet<>();
        Map<String, Set<String>> activeVersions = new HashMap<>();
        for (JsonNode entry : activeCatalog) {
            String id = entry.path("schemaId").asText();
            String version = entry.path("version").asText();
            activeIdentities.add(id + "@" + version);
            activeVersions.computeIfAbsent(id, ignored -> new HashSet<>()).add(version);
        }

        SchemaCompiler compiler = new SchemaCompiler(mapper);
        Set<String> historyIdentities = new HashSet<>();
        for (JsonNode entry : history) {
            String id = entry.path("schemaId").asText();
            String version = entry.path("version").asText();
            String identity = id + "@" + version;
            assertTrue(historyIdentities.add(identity), "duplicate archived identity " + identity);
            assertFalse(activeIdentities.contains(identity), "archived version is still an active source " + identity);
            Set<String> replacements = activeVersions.get(id);
            assertTrue(replacements != null, "archived schema has no current source " + id);
            assertTrue(replacements.stream().anyMatch(candidate -> compareVersions(candidate, version) > 0),
                    "archived version has no newer current source " + identity);

            JsonNode definition = entry.path("definition").deepCopy();
            if (definition instanceof ObjectNode object) object.put("model", entry.path("model").asText());
            compiler.compile(definition, id);
        }
        assertFalse(historyIdentities.isEmpty(), "historical version archive must not be empty");
    }

    @Test
    void bootstrapSeedsArchivedVersionsAsRetiredBeforeApprovedCatalogVersions() throws Exception {
        int archivedCount;
        int activeCount;
        try (InputStream archived = new ClassPathResource("schemas/history/published-versions.json").getInputStream();
             InputStream active = new ClassPathResource("schemas/catalog.json").getInputStream()) {
            archivedCount = mapper.readTree(archived).size();
            activeCount = mapper.readTree(active).size();
        }
        SchemaVersionRepository schemas = mock(SchemaVersionRepository.class);
        SolverVersionRepository solvers = mock(SolverVersionRepository.class);
        SchemaDefinitionService definitions = mock(SchemaDefinitionService.class);
        PhysicsSolverRegistry numerical = mock(PhysicsSolverRegistry.class);
        ReferenceSolverRegistry references = mock(ReferenceSolverRegistry.class);
        when(schemas.findFirstBySchemaIdAndVersion(anyString(), anyString())).thenReturn(Optional.empty());
        when(solvers.findFirstBySchemaIdAndVersion(anyString(), anyString())).thenReturn(Optional.empty());
        when(definitions.compiledChecksum(any())).thenReturn("fixture-checksum");
        when(numerical.get(anyString())).thenReturn(mock(PhysicsSolver.class));
        when(references.get(anyString())).thenReturn(mock(ReferenceSolver.class));
        when(schemas.save(any(SchemaVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(solvers.save(any(SolverVersion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var initializer = new PhysicsCatalogInitializer(schemas, solvers, mapper, definitions, numerical, references,
                new PhysicsModuleConfiguration().physicsModuleRegistry());
        initializer.run();

        ArgumentCaptor<SchemaVersion> schemaCaptor = ArgumentCaptor.forClass(SchemaVersion.class);
        ArgumentCaptor<SolverVersion> solverCaptor = ArgumentCaptor.forClass(SolverVersion.class);
        verify(schemas, times(archivedCount + activeCount)).save(schemaCaptor.capture());
        verify(solvers, times(archivedCount + activeCount)).save(solverCaptor.capture());
        assertEquals(archivedCount, schemaCaptor.getAllValues().stream()
                .filter(item -> item.getLifecycleStatus() == LifecycleStatus.RETIRED).count());
        assertEquals(activeCount, schemaCaptor.getAllValues().stream()
                .filter(item -> item.getLifecycleStatus() == LifecycleStatus.APPROVED).count());
        assertEquals(archivedCount, solverCaptor.getAllValues().stream()
                .filter(item -> item.getLifecycleStatus() == LifecycleStatus.RETIRED).count());
        assertEquals(activeCount, solverCaptor.getAllValues().stream()
                .filter(item -> item.getLifecycleStatus() == LifecycleStatus.APPROVED).count());
    }

    private int compareVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        for (int index = 0; index < Math.max(leftParts.length, rightParts.length); index++) {
            int leftValue = index < leftParts.length ? Integer.parseInt(leftParts[index]) : 0;
            int rightValue = index < rightParts.length ? Integer.parseInt(rightParts[index]) : 0;
            if (leftValue != rightValue) return Integer.compare(leftValue, rightValue);
        }
        return 0;
    }
}
