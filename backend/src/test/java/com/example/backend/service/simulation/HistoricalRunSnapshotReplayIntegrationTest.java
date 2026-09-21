package com.example.backend.service.simulation;

import com.example.backend.ai.normalization.UnitNormalizer;
import com.example.backend.entity.account.User;
import com.example.backend.entity.assignment.Assignment;
import com.example.backend.entity.enums.AssignmentStatus;
import com.example.backend.entity.enums.ConfirmationState;
import com.example.backend.entity.enums.ExtractionOutcome;
import com.example.backend.entity.enums.ExtractionPath;
import com.example.backend.entity.enums.ExtractionRunStatus;
import com.example.backend.entity.enums.SimulationStatus;
import com.example.backend.entity.enums.SourceMode;
import com.example.backend.entity.enums.SubmissionStatus;
import com.example.backend.entity.problem.ExtractionRun;
import com.example.backend.entity.problem.ProblemSubmission;
import com.example.backend.entity.problem.Specification;
import com.example.backend.entity.simulation.Simulation;
import com.example.backend.entity.simulation.SimulationRun;
import com.example.backend.entity.library.LibraryItem;
import com.example.backend.entity.enums.LibraryModerationStatus;
import com.example.backend.entity.enums.Visibility;
import com.example.backend.physics.binding.CanonicalQuantityCompiler;
import com.example.backend.physics.compatibility.LegacyPhysicsExecutionAdapterV1;
import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolverRegistry;
import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.repository.library.LibraryItemRepository;
import com.example.backend.repository.assignment.AssignmentRepository;
import com.example.backend.repository.assignment.AssignmentSubmissionRepository;
import com.example.backend.repository.problem.SpecificationRepository;
import com.example.backend.repository.simulation.SimulationRepository;
import com.example.backend.repository.simulation.SimulationRunRepository;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.example.backend.service.problem.SpecificationReadinessService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Persists and reloads a pinned run snapshot before exercising the replay service. */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class HistoricalRunSnapshotReplayIntegrationTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired private EntityManager entityManager;
    @Autowired private SimulationRepository simulations;
    @Autowired private SimulationRunRepository runs;
    @Autowired private AssignmentRepository assignments;
    @Autowired private AssignmentSubmissionRepository assignmentSubmissions;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void reloadsHistoricalSnapshotAndReplaysWithoutResolvingLatestSchema() throws Exception {
        User owner = new User();
        owner.setEmail("historical-replay@example.test");
        owner.setPassword("fixture-password");
        owner.setFullName("Historical Replay Fixture");
        entityManager.persist(owner);

        ProblemSubmission submission = new ProblemSubmission();
        submission.setOwner(owner);
        submission.setSourceMode(SourceMode.TEXT);
        submission.setOriginalText("legacy fixture");
        submission.setEditableText("legacy fixture");
        submission.setStatus(SubmissionStatus.EXTRACTED);
        entityManager.persist(submission);

        ExtractionRun extraction = new ExtractionRun();
        extraction.setSubmission(submission);
        extraction.setExtractionPath(ExtractionPath.RULE_BASED);
        extraction.setProviderName("historical-fixture");
        extraction.setModelVersion("fixture-v1");
        extraction.setStatus(ExtractionRunStatus.SUCCEEDED);
        extraction.setOutcome(ExtractionOutcome.RULE_BASED_FALLBACK);
        entityManager.persist(extraction);

        Specification specification = new Specification();
        specification.setSubmission(submission);
        specification.setExtractionRun(extraction);
        specification.setSchemaId("retired_fixture");
        specification.setSchemaVersion("1.0");
        specification.setContractVersion("1.0");
        specification.setTopic("KINEMATICS");
        specification.setConfidence(BigDecimal.ONE);
        specification.setObjects(mapper.createArrayNode());
        specification.setQuantities(mapper.createArrayNode());
        specification.setRelations(mapper.createArrayNode());
        specification.setEndCondition(mapper.createObjectNode().put("type", "time_limit").put("duration", 1));
        specification.setAmbiguity(mapper.createArrayNode());
        specification.setConfirmationState(ConfirmationState.CONFIRMED);
        entityManager.persist(specification);
        submission.setCurrentSpecification(specification);

        Simulation simulation = new Simulation();
        simulation.setSpecification(specification);
        simulation.setOwner(owner);
        simulation.setSchemaId("retired_fixture");
        simulation.setSolverVersion("legacy-binding-v1");
        simulation.setStatus(SimulationStatus.ARCHIVED);
        entityManager.persist(simulation);
        entityManager.flush();

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);
        run.setRunType("INITIAL");
        run.setSchemaId("retired_fixture");
        run.setSchemaVersion("1.0");
        run.setBindingVersion("legacy-binding-v1");
        run.setNumericalSolverId("legacy-numerical");
        run.setReferenceSolverId("legacy-reference");
        run.setOutputContractVersion("1.0");
        run.setOutputContractChecksum("historical-contract-checksum");
        run.setValidationPassed(true);
        run.setValidationCheckpoints(mapper.createArrayNode());
        run.setValidationError(null);
        run.setDurationSeconds(1.0);
        run.setResult(mapper.readTree("""
                {
                  "schemaId":"retired_fixture",
                  "schemaVersion":"1.0",
                  "bindingVersion":"legacy-binding-v1",
                  "numericalSolverId":"legacy-numerical",
                  "referenceSolverId":"legacy-reference",
                  "outputContractVersion":"1.0",
                  "outputContractChecksum":"historical-contract-checksum",
                  "time":[0.0,1.0],
                  "values":{"position":[0.0,1.0]},
                  "positions":{},"velocities":{},"accelerations":{},"scalarOutputs":{},"scalarFields":{},
                  "parameters":{"speed":1.0},
                  "validation":{"passed":true},
                  "resolvedEnd":{"time":1.0,"reason":"time_limit","conditionReached":true}
                }
                """));
        entityManager.persist(run);
        entityManager.flush();
        simulation.setLatestRun(run);
        entityManager.flush();

        var snapshotId = run.getId();
        entityManager.clear();
        Simulation reloaded = simulations.findById(simulation.getId()).orElseThrow();
        SimulationRun reloadedRun = runs.findById(snapshotId).orElseThrow();
        assertEquals("retired_fixture", reloadedRun.getResult().path("schemaId").asText());
        assertEquals("1.0", reloadedRun.getResult().path("schemaVersion").asText());
        assertEquals("1.0", reloadedRun.getResult().path("outputContractVersion").asText());
        assertEquals("retired_fixture", reloadedRun.getSchemaId());
        assertEquals("1.0", reloadedRun.getSchemaVersion());
        assertEquals("legacy-binding-v1", reloadedRun.getBindingVersion());
        assertEquals("historical-contract-checksum", reloadedRun.getOutputContractChecksum());

        SimulationService replayService = new SimulationService(
                simulations,
                mock(LibraryItemRepository.class),
                runs,
                mock(SpecificationRepository.class),
                mock(PhysicsSolverRegistry.class),
                mock(PhysicsValidationService.class),
                mock(com.example.backend.service.account.CurrentUserService.class),
                mapper,
                mock(SchemaDefinitionService.class),
                mock(SpecificationReadinessService.class),
                new CanonicalQuantityCompiler(new UnitNormalizer(mapper)),
                new PhysicsModuleRegistry(List.of()),
                new LegacyPhysicsExecutionAdapterV1(List.of()));

        var response = replayService.replay(reloaded, snapshotId);
        assertNotNull(response);
        assertEquals(snapshotId, response.simulationRunId());
        assertTrue(response.success());
        assertTrue(response.validationPassed());
        assertEquals(List.of(0.0, 1.0), response.time());
        assertEquals(List.of(0.0, 1.0), response.values().get("position"));
        assertEquals(1.0, response.rawResult().path("resolvedEnd").path("time").asDouble(), 1e-12);
        assertEquals("legacy-binding-v1", reloaded.getSolverVersion());
    }

    @Test
    void reloadsAssignmentPinnedRunAndReplaysThroughApplicationServices() throws Exception {
        User teacher = new User();
        teacher.setEmail("assignment-teacher@example.test");
        teacher.setPassword("fixture-password");
        teacher.setFullName("Assignment Teacher");
        entityManager.persist(teacher);

        User student = new User();
        student.setEmail("assignment-student@example.test");
        student.setPassword("fixture-password");
        student.setFullName("Assignment Student");
        entityManager.persist(student);

        ProblemSubmission submission = new ProblemSubmission();
        submission.setOwner(teacher);
        submission.setSourceMode(SourceMode.TEXT);
        submission.setOriginalText("assignment fixture");
        submission.setEditableText("assignment fixture");
        submission.setStatus(SubmissionStatus.EXTRACTED);
        entityManager.persist(submission);

        ExtractionRun extraction = new ExtractionRun();
        extraction.setSubmission(submission);
        extraction.setExtractionPath(ExtractionPath.RULE_BASED);
        extraction.setProviderName("assignment-fixture");
        extraction.setModelVersion("fixture-v1");
        extraction.setStatus(ExtractionRunStatus.SUCCEEDED);
        extraction.setOutcome(ExtractionOutcome.RULE_BASED_FALLBACK);
        entityManager.persist(extraction);

        Specification specification = new Specification();
        specification.setSubmission(submission);
        specification.setExtractionRun(extraction);
        specification.setSchemaId("retired_assignment_fixture");
        specification.setSchemaVersion("1.0");
        specification.setContractVersion("1.0");
        specification.setTopic("KINEMATICS");
        specification.setConfidence(BigDecimal.ONE);
        specification.setObjects(mapper.createArrayNode());
        specification.setQuantities(mapper.createArrayNode());
        specification.setRelations(mapper.createArrayNode());
        specification.setEndCondition(mapper.createObjectNode().put("type", "time_limit").put("duration", 1));
        specification.setAmbiguity(mapper.createArrayNode());
        specification.setConfirmationState(ConfirmationState.CONFIRMED);
        entityManager.persist(specification);
        submission.setCurrentSpecification(specification);

        Simulation simulation = new Simulation();
        simulation.setSpecification(specification);
        simulation.setOwner(teacher);
        simulation.setSchemaId("retired_assignment_fixture");
        simulation.setSolverVersion("legacy-assignment-v1");
        simulation.setStatus(SimulationStatus.ARCHIVED);
        entityManager.persist(simulation);
        entityManager.flush();

        SimulationRun run = new SimulationRun();
        run.setSimulation(simulation);
        run.setRunType("INITIAL");
        run.setSchemaId("retired_assignment_fixture");
        run.setSchemaVersion("1.0");
        run.setBindingVersion("legacy-assignment-v1");
        run.setNumericalSolverId("legacy-assignment-numerical");
        run.setReferenceSolverId("legacy-assignment-reference");
        run.setOutputContractVersion("1.0");
        run.setOutputContractChecksum("assignment-contract-checksum");
        run.setValidationPassed(true);
        run.setValidationCheckpoints(mapper.createArrayNode());
        run.setDurationSeconds(1.0);
        run.setResult(mapper.readTree("""
                {
                  "schemaId":"retired_assignment_fixture",
                  "schemaVersion":"1.0",
                  "bindingVersion":"legacy-assignment-v1",
                  "numericalSolverId":"legacy-assignment-numerical",
                  "referenceSolverId":"legacy-assignment-reference",
                  "outputContractVersion":"1.0",
                  "outputContractChecksum":"assignment-contract-checksum",
                  "time":[0.0,1.0],
                  "values":{"position":[0.0,1.0]},
                  "positions":{},"velocities":{},"accelerations":{},"scalarOutputs":{},"scalarFields":{},
                  "parameters":{"speed":1.0},
                  "validation":{"passed":true},
                  "resolvedEnd":{"time":1.0,"reason":"time_limit","conditionReached":true}
                }
                """));
        entityManager.persist(run);
        entityManager.flush();
        simulation.setLatestRun(run);
        entityManager.flush();

        LibraryItem libraryItem = new LibraryItem();
        libraryItem.setSimulation(simulation);
        libraryItem.setSpecification(specification);
        libraryItem.setOwner(teacher);
        libraryItem.setTitle("Pinned assignment fixture");
        libraryItem.setVisibility(Visibility.PERSONAL);
        libraryItem.setModerationStatus(LibraryModerationStatus.APPROVED);
        libraryItem.setActive(true);
        entityManager.persist(libraryItem);

        Assignment assignment = new Assignment();
        assignment.setLibraryItem(libraryItem);
        assignment.setSpecification(specification);
        assignment.setTeacher(teacher);
        assignment.setAssignedSimulationRunId(run.getId());
        assignment.setTitle("Replay pinned run");
        assignment.setQuestions(mapper.createObjectNode()
                .put("activityType", "FREE_EXPLORATION")
                .put("prompt", "Inspect the pinned simulation"));
        assignment.setMaxScore(BigDecimal.TEN);
        assignment.setAutoGrade(false);
        assignment.setAssignedStudentIds(Set.of(student.getId()));
        assignment.setAssignedAt(Instant.parse("2026-01-01T00:00:00Z"));
        assignment.setStatus(AssignmentStatus.ACTIVE);
        entityManager.persist(assignment);
        entityManager.flush();

        UUID assignmentId = assignment.getId();
        UUID runId = run.getId();
        entityManager.clear();
        Assignment reloadedAssignment = assignments.findById(assignmentId).orElseThrow();
        Simulation reloadedSimulation = simulations.findById(simulation.getId()).orElseThrow();
        assertEquals(runId, reloadedAssignment.getAssignedSimulationRunId());

        var replayService = new SimulationService(
                simulations,
                mock(LibraryItemRepository.class),
                runs,
                mock(SpecificationRepository.class),
                mock(PhysicsSolverRegistry.class),
                mock(PhysicsValidationService.class),
                mock(com.example.backend.service.account.CurrentUserService.class),
                mapper,
                mock(SchemaDefinitionService.class),
                mock(SpecificationReadinessService.class),
                new CanonicalQuantityCompiler(new UnitNormalizer(mapper)),
                new PhysicsModuleRegistry(List.of()),
                new LegacyPhysicsExecutionAdapterV1(List.of()));
        assertEquals(runId, replayService.latestRunId(reloadedSimulation));
        assertEquals(runId, replayService.runIdAtOrBefore(reloadedSimulation, Instant.now()));
        var currentUser = mock(com.example.backend.service.account.CurrentUserService.class);
        org.mockito.Mockito.when(currentUser.requireCurrentUser()).thenReturn(student);
        var assignmentService = new com.example.backend.service.assignment.AssignmentService(
                assignments, assignmentSubmissions, null, null, currentUser, replayService, null, null);

        var response = assignmentService.simulationForStudent(assignmentId);

        assertEquals(runId, response.simulationRunId());
        assertEquals(List.of(0.0, 1.0), response.time());
        assertEquals(List.of(0.0, 1.0), response.values().get("position"));
        assertEquals("REPLAY", response.message());
        assertEquals(reloadedSimulation.getId(), response.simulationId());
    }
}
