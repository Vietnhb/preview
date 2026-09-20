package com.example.backend.service.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.backend.entity.enums.LifecycleStatus;
import com.example.backend.entity.curriculum.Topic;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.entity.simulation.SolverVersion;
import com.example.backend.physics.compatibility.LegacySchemaIdentityAdapter;
import com.example.backend.repository.curriculum.TopicRepository;
import com.example.backend.repository.problem.SchemaVersionRepository;
import com.example.backend.repository.simulation.SolverVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

class SchemaIdentityResolutionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void currentRequestLookupDoesNotStripDimensionalSuffixes() {
        SchemaVersionRepository schemas = mock(SchemaVersionRepository.class);
        SchemaDefinitionService service = new SchemaDefinitionService(
                schemas, mock(SolverVersionRepository.class), mock(TopicRepository.class));

        assertThrows(RuntimeException.class, () -> service.requireCurrentApproved("motion_2d", "1.0"));

        verify(schemas).findFirstBySchemaIdAndVersion("motion_2d", "1.0");
        verify(schemas, never()).findFirstBySchemaIdAndVersion("motion", "1.0");
    }

    @Test
    void latestApprovedVersionCheckUsesEnabledCatalogDataAndVersionOrdering() {
        SchemaVersionRepository schemas = mock(SchemaVersionRepository.class);
        TopicRepository topics = mock(TopicRepository.class);
        Topic enabled = new Topic();
        enabled.setName("WAVES");
        enabled.setEnabled(true);
        SchemaVersion older = new SchemaVersion();
        older.setSchemaId("motion");
        older.setVersion("1.9");
        older.setTopic("WAVES");
        SchemaVersion latest = new SchemaVersion();
        latest.setSchemaId("motion");
        latest.setVersion("1.10");
        latest.setTopic("WAVES");
        when(topics.findByEnabledTrueOrderBySortOrderAsc()).thenReturn(List.of(enabled));
        when(schemas.findAllBySchemaIdIgnoreCaseAndLifecycleStatusAndTopicInOrderByCreatedAtDesc(
                "motion", LifecycleStatus.APPROVED, List.of("WAVES"))).thenReturn(List.of(older, latest));
        SchemaDefinitionService service = new SchemaDefinitionService(
                schemas, mock(SolverVersionRepository.class), topics);

        org.junit.jupiter.api.Assertions.assertFalse(service.isLatestApprovedEnabledVersion("motion", "1.9"));
        org.junit.jupiter.api.Assertions.assertTrue(service.isLatestApprovedEnabledVersion("motion", "1.10"));
    }

    @Test
    void currentCandidateResolverRejectsAFormerlyApprovedVersion() {
        SchemaVersionRepository schemas = mock(SchemaVersionRepository.class);
        SchemaVersion retired = new SchemaVersion();
        retired.setSchemaId("motion");
        retired.setVersion("1.0");
        retired.setTopic("WAVES");
        retired.setLifecycleStatus(LifecycleStatus.RETIRED);
        when(schemas.findFirstBySchemaIdAndVersion("motion", "1.0")).thenReturn(Optional.of(retired));
        SchemaDefinitionService service = new SchemaDefinitionService(
                schemas, mock(SolverVersionRepository.class), mock(TopicRepository.class));

        assertThrows(RuntimeException.class, () -> service.requireCurrentApproved("motion", "1.0"));
    }

    @Test
    void pinnedReplayUsesTheExplicitVersionedLegacyIdentityAdapter() throws Exception {
        SchemaVersionRepository schemas = mock(SchemaVersionRepository.class);
        SolverVersionRepository solvers = mock(SolverVersionRepository.class);
        TopicRepository topics = mock(TopicRepository.class);
        SchemaVersion legacySchema = schema("motion", "1.0", LifecycleStatus.RETIRED);
        SolverVersion historicalBinding = solverBinding(
                "motion", "1.0", "motion_solver_v1", "motion_reference_v1", LifecycleStatus.RETIRED);
        SolverVersion currentBinding = solverBinding(
                "motion", "2.0", "motion_solver_v2", "motion_reference_v2", LifecycleStatus.APPROVED);
        when(schemas.findFirstBySchemaIdAndVersion("motion_2d", "1.0")).thenReturn(Optional.empty());
        when(schemas.findFirstBySchemaIdAndVersion("motion", "1.0")).thenReturn(Optional.of(legacySchema));
        when(solvers.findFirstBySchemaIdAndVersion("motion", "1.0")).thenReturn(Optional.of(historicalBinding));
        when(solvers.findFirstBySchemaIdAndVersion("motion", "2.0")).thenReturn(Optional.of(currentBinding));
        when(topics.existsByNameIgnoreCaseAndEnabledTrue("WAVES")).thenReturn(true);
        SchemaDefinitionService service = new SchemaDefinitionService(
                schemas, solvers, topics);

        SchemaVersion replayed = service.requirePublishedVersion("motion_2d", "1.0");

        assertEquals("motion", replayed.getSchemaId());
        assertEquals("1.0", replayed.getVersion());
        assertEquals(LifecycleStatus.RETIRED, replayed.getLifecycleStatus());
        assertEquals("motion", LegacySchemaIdentityAdapter.adaptForReplay(
                "motion_2d", LegacySchemaIdentityAdapter.VERSION));
        assertThrows(IllegalArgumentException.class, () -> LegacySchemaIdentityAdapter.adaptForReplay(
                "motion_2d", "legacy-dimensional-suffix-v999"));

        SchemaDefinitionService.SolverBinding binding = service.requireSolverBinding(
                replayed.getSchemaId(), replayed.getVersion());
        assertEquals(new SchemaDefinitionService.SolverBinding(
                "motion_solver_v1", "motion_reference_v1", "1.0"), binding);

        verify(schemas).findFirstBySchemaIdAndVersion("motion_2d", "1.0");
        verify(schemas).findFirstBySchemaIdAndVersion("motion", "1.0");
        verify(solvers).findFirstBySchemaIdAndVersion("motion", "1.0");
        verify(solvers, never()).findFirstBySchemaIdAndVersion("motion_2d", "1.0");
        verify(solvers, never()).findFirstBySchemaIdAndVersion("motion", "2.0");
        verify(solvers, never()).findFirstBySchemaIdAndLifecycleStatusOrderByCreatedAtDesc(
                "motion", LifecycleStatus.APPROVED);
    }

    private SchemaVersion schema(String id, String version, LifecycleStatus status) throws Exception {
        SchemaVersion schema = new SchemaVersion();
        schema.setSchemaId(id);
        schema.setVersion(version);
        schema.setTopic("WAVES");
        schema.setName("Motion fixture");
        schema.setLifecycleStatus(status);
        schema.setDefinition(mapper.readTree("""
                {
                  "model":"motion",
                  "requiredQuantities":[],"optionalQuantities":[],"adjustableParameters":[],
                  "execution":{"durationSeconds":1,"stepSeconds":0.1,"durationBindings":[]},
                  "validation":{"tolerance":0.000001,"checkpointFractions":[1]},
                  "output":{"probeSeries":[]},
                  "visualization":{"scene":"fixture","series":[],"presentation":{"actors":[{}]}}
                }
                """));
        return schema;
    }

    private SolverVersion solverBinding(String schemaId, String version, String numericalId,
            String referenceId, LifecycleStatus status) throws Exception {
        SolverVersion solver = new SolverVersion();
        solver.setSchemaId(schemaId);
        solver.setVersion(version);
        solver.setSolverId(numericalId);
        solver.setLifecycleStatus(status);
        solver.setOutputDefinition(mapper.readTree("{\"referenceSolverId\":\"" + referenceId + "\"}"));
        return solver;
    }
}
