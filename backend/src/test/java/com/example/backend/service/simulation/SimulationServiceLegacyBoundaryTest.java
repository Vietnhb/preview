package com.example.backend.service.simulation;

import com.example.backend.dto.simulation.SimulationRequest;
import com.example.backend.entity.account.User;
import com.example.backend.entity.enums.ConfirmationState;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.entity.problem.Specification;
import com.example.backend.entity.simulation.Simulation;
import com.example.backend.entity.simulation.SimulationRun;
import com.example.backend.entity.enums.SimulationStatus;
import com.example.backend.exception.ApiException;
import com.example.backend.exception.SolverBindingException;
import com.example.backend.physics.binding.CanonicalQuantityCompiler;
import com.example.backend.physics.compatibility.LegacyPhysicsExecutionAdapterV1;
import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;
import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolverRegistry;
import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolverRegistry;
import com.example.backend.repository.curriculum.TopicRepository;
import com.example.backend.repository.library.LibraryItemRepository;
import com.example.backend.repository.problem.SchemaVersionRepository;
import com.example.backend.repository.problem.SpecificationRepository;
import com.example.backend.repository.simulation.SimulationRepository;
import com.example.backend.repository.simulation.SimulationRunRepository;
import com.example.backend.repository.simulation.SolverVersionRepository;
import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.problem.SchemaCompiler;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.example.backend.service.problem.SpecificationReadinessService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimulationServiceLegacyBoundaryTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void currentMissingTypedPairFailsBeforeLegacyLookupOrPersistence() throws Exception {
        UUID specificationId = UUID.randomUUID();
        User user = new User();
        Specification specification = new Specification();
        specification.setId(specificationId);
        specification.setSchemaId("current_untyped_model");
        specification.setSchemaVersion("2.0");
        specification.setConfirmationState(ConfirmationState.CONFIRMED);
        JsonNode input = mapper.readTree("{\"quantities\":[],\"relations\":[]}");
        SchemaVersion schema = schema("current_untyped_model", "2.0", "TEST");
        var compiled = new SchemaCompiler(mapper).compile(schema.getDefinition(), schema.getSchemaId());

        SimulationRepository simulations = mock(SimulationRepository.class);
        SimulationRunRepository runs = mock(SimulationRunRepository.class);
        SpecificationRepository specifications = mock(SpecificationRepository.class);
        PhysicsSolverRegistry legacySolvers = mock(PhysicsSolverRegistry.class);
        SchemaDefinitionService definitions = mock(SchemaDefinitionService.class);
        SpecificationReadinessService readiness = mock(SpecificationReadinessService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.requireCurrentUser()).thenReturn(user);
        when(specifications.findByIdAndSubmissionOwner(specificationId, user)).thenReturn(Optional.of(specification));
        when(readiness.blockers(specification)).thenReturn(List.of());
        when(readiness.toJson(specification)).thenReturn(input);
        when(definitions.requireCurrentApproved("current_untyped_model", "2.0")).thenReturn(schema);
        when(definitions.requireSolverBinding("current_untyped_model", "2.0"))
                .thenReturn(new SchemaDefinitionService.SolverBinding(
                        "untyped-numerical", "untyped-reference", "binding-v3"));
        when(definitions.effectiveAdjustments(eq(input), eq(schema.getDefinition()), eq(Map.of()), eq(Map.of())))
                .thenReturn(Map.of());
        when(definitions.durationSeconds(input, schema.getDefinition())).thenReturn(1.0);
        when(definitions.compiled(schema)).thenReturn(compiled);
        when(definitions.isLatestApprovedEnabledVersion("current_untyped_model", "2.0")).thenReturn(true);

        LegacyPhysicsExecutionAdapterV1 adapter = new LegacyPhysicsExecutionAdapterV1(List.of());
        var validation = new PhysicsValidationService(new ReferenceSolverRegistry(List.of()), definitions, adapter);
        SimulationService service = service(simulations, runs, specifications, legacySolvers,
                definitions, readiness, currentUser, validation, adapter, new PhysicsModuleRegistry(List.of()));

        assertThrows(SolverBindingException.class,
                () -> service.run(new SimulationRequest(specificationId, Map.of())));

        verify(legacySolvers, never()).get(anyString());
        verify(simulations, never()).save(any());
        verify(runs, never()).save(any());
    }

    @Test
    void currentSuffixedIdentityUsesExactLookupAndNeverEntersReplayAdapter() throws Exception {
        UUID specificationId = UUID.randomUUID();
        User user = new User();
        Specification specification = new Specification();
        specification.setId(specificationId);
        specification.setSchemaId("motion_2d");
        specification.setSchemaVersion("1.0");
        specification.setConfirmationState(ConfirmationState.CONFIRMED);
        JsonNode input = mapper.readTree("{\"quantities\":[],\"relations\":[]}");

        SchemaVersionRepository schemas = mock(SchemaVersionRepository.class);
        SolverVersionRepository solverVersions = mock(SolverVersionRepository.class);
        TopicRepository topics = mock(TopicRepository.class);
        SchemaDefinitionService definitions = new SchemaDefinitionService(schemas, solverVersions, topics);
        SimulationRepository simulations = mock(SimulationRepository.class);
        SimulationRunRepository runs = mock(SimulationRunRepository.class);
        SpecificationRepository specifications = mock(SpecificationRepository.class);
        PhysicsSolverRegistry legacySolvers = mock(PhysicsSolverRegistry.class);
        SpecificationReadinessService readiness = mock(SpecificationReadinessService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        when(currentUser.requireCurrentUser()).thenReturn(user);
        when(specifications.findByIdAndSubmissionOwner(specificationId, user)).thenReturn(Optional.of(specification));
        when(readiness.blockers(specification)).thenReturn(List.of());
        when(readiness.toJson(specification)).thenReturn(input);

        LegacyPhysicsExecutionAdapterV1 adapter = new LegacyPhysicsExecutionAdapterV1(List.of());
        var validation = new PhysicsValidationService(new ReferenceSolverRegistry(List.of()), definitions, adapter);
        SimulationService service = service(simulations, runs, specifications, legacySolvers,
                definitions, readiness, currentUser, validation, adapter, new PhysicsModuleRegistry(List.of()));

        assertThrows(ApiException.class,
                () -> service.run(new SimulationRequest(specificationId, Map.of())));

        verify(schemas).findFirstBySchemaIdAndVersion("motion_2d", "1.0");
        verify(schemas, never()).findFirstBySchemaIdAndVersion("motion", "1.0");
        verify(solverVersions, never()).findFirstBySchemaIdAndVersion(anyString(), anyString());
        verify(legacySolvers, never()).get(anyString());
        verify(simulations, never()).save(any());
        verify(runs, never()).save(any());
    }

    @Test
    void replayRejectsAStoredSnapshotWhosePinnedIdentityDoesNotMatchItsSimulation() {
        UUID simulationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        Specification specification = new Specification();
        specification.setSchemaId("retired-model");
        specification.setSchemaVersion("1.0");
        Simulation simulation = new Simulation();
        simulation.setId(simulationId);
        simulation.setSchemaId("retired-model");
        simulation.setSpecification(specification);
        simulation.setStatus(SimulationStatus.ARCHIVED);

        SimulationRun run = new SimulationRun();
        run.setId(runId);
        run.setSimulation(simulation);
        run.setSchemaId("different-model");
        run.setSchemaVersion("1.0");
        run.setResult(mapper.createObjectNode());

        SimulationRunRepository runs = mock(SimulationRunRepository.class);
        when(runs.findById(runId)).thenReturn(Optional.of(run));
        SimulationService service = service(mock(SimulationRepository.class), runs,
                mock(SpecificationRepository.class), mock(PhysicsSolverRegistry.class),
                mock(SchemaDefinitionService.class), mock(SpecificationReadinessService.class),
                mock(CurrentUserService.class), mock(PhysicsValidationService.class),
                new LegacyPhysicsExecutionAdapterV1(List.of()), new PhysicsModuleRegistry(List.of()));

        assertThrows(ApiException.class, () -> service.replay(simulation, runId));
    }

    @Test
    void historicalPreviewReexecutionUsesOnlyAnExactLegacyPermit() throws Exception {
        UUID simulationId = UUID.randomUUID();
        Specification specification = new Specification();
        specification.setId(UUID.randomUUID());
        specification.setSchemaId("historical_kinematics");
        specification.setSchemaVersion("1.0");
        specification.setConfirmationState(ConfirmationState.CONFIRMED);

        JsonNode definition = mapper.readTree("""
                {
                  "version":"1.0", "topic":"RETIRED_KINEMATICS", "model":"uniform_acceleration",
                  "requiredQuantities":[], "optionalQuantities":[], "adjustableParameters":[],
                  "execution":{"durationSeconds":1,"stepSeconds":0.5,"durationBindings":[]},
                  "validation":{"tolerance":0.01,"checkpointFractions":[1]},
                  "output":{"type":"timeseries"},
                  "visualization":{"scene":"fixture","series":[
                    {"key":"x","source":"values.x","unit":"m"}
                  ],"presentation":{"actors":[{"id":"body"}]}}
                }
                """);
        SchemaVersion schema = schema("historical_kinematics", "1.0", "RETIRED_KINEMATICS");
        schema.setDefinition(definition);
        var compiled = new SchemaCompiler(mapper).compile(definition,
                "historical_kinematics", "1.0", "RETIRED_KINEMATICS");

        var historicalResult = mapper.createObjectNode();
        historicalResult.set("time", mapper.valueToTree(List.of(0.0, 1.0)));
        historicalResult.set("parameters", mapper.createObjectNode());
        SimulationRun baseRun = new SimulationRun();
        baseRun.setResult(historicalResult);
        Simulation simulation = new Simulation();
        simulation.setId(simulationId);
        simulation.setSpecification(specification);
        simulation.setSchemaId("historical_kinematics");
        simulation.setLatestRun(baseRun);

        PhysicsSolver solver = mock(PhysicsSolver.class);
        when(solver.solverId()).thenReturn("historical-numerical");
        SolverOutput solverOutput = new SolverOutput(
                List.of(0.0, 0.5, 1.0),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of("x", List.of(0.0, 0.5, 1.0)));
        when(solver.solve(any(), any(), eq(1.0), eq(0.5))).thenReturn(solverOutput);
        PhysicsSolverRegistry legacySolvers = new PhysicsSolverRegistry(List.of(solver));
        var permit = new LegacyPhysicsExecutionAdapterV1.Permit(
                "historical_kinematics", "1.0", "legacy-binding-v1",
                "historical-numerical", "historical-reference");
        LegacyPhysicsExecutionAdapterV1 adapter = new LegacyPhysicsExecutionAdapterV1(List.of(permit));

        SimulationRepository simulations = mock(SimulationRepository.class);
        SimulationRunRepository runs = mock(SimulationRunRepository.class);
        SpecificationRepository specifications = mock(SpecificationRepository.class);
        SchemaDefinitionService definitions = mock(SchemaDefinitionService.class);
        SpecificationReadinessService readiness = mock(SpecificationReadinessService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        PhysicsValidationService validation = mock(PhysicsValidationService.class);
        JsonNode input = mapper.readTree("{\"quantities\":[],\"relations\":[]}");
        when(readiness.blockers(specification)).thenReturn(List.of());
        when(readiness.toJson(specification)).thenReturn(input);
        when(definitions.requirePublishedVersion("historical_kinematics", "1.0")).thenReturn(schema);
        when(definitions.requireSolverBinding("historical_kinematics", "1.0"))
                .thenReturn(new SchemaDefinitionService.SolverBinding(
                        "historical-numerical", "historical-reference", "legacy-binding-v1"));
        when(definitions.effectiveAdjustments(any(), any(), any(), any())).thenReturn(Map.of());
        when(definitions.durationSeconds(input, definition)).thenReturn(1.0);
        when(definitions.compiled(schema)).thenReturn(compiled);
        when(definitions.isLatestApprovedEnabledVersion("historical_kinematics", "1.0"))
                .thenReturn(false);
        when(definitions.visualization(definition)).thenReturn(mapper.createObjectNode());
        when(validation.validate(any(), eq("historical_kinematics"), eq("1.0"), any(), any(), eq(null)))
                .thenReturn(new com.example.backend.dto.simulation.ValidationResponse(
                        null, true, "historical_kinematics", 0.01, List.of(), List.of(), 0));

        SimulationService service = service(simulations, runs, specifications, legacySolvers,
                definitions, readiness, currentUser, validation, adapter, new PhysicsModuleRegistry(List.of()));

        var response = service.previewAdjustment(simulation, null, Map.of());

        assertTrue(response.success());
        assertEquals(List.of(0.0, 0.5, 1.0), response.time());
        verify(solver).solve(any(), eq(Map.of()), eq(1.0), eq(0.5));
    }

    private SimulationService service(SimulationRepository simulations, SimulationRunRepository runs,
            SpecificationRepository specifications, PhysicsSolverRegistry legacySolvers,
            SchemaDefinitionService definitions, SpecificationReadinessService readiness,
            CurrentUserService currentUser, PhysicsValidationService validation,
            LegacyPhysicsExecutionAdapterV1 adapter, PhysicsModuleRegistry modules) {
        return new SimulationService(simulations, mock(LibraryItemRepository.class), runs, specifications,
                legacySolvers, validation, currentUser, mapper, definitions, readiness,
                new CanonicalQuantityCompiler(new com.example.backend.ai.normalization.UnitNormalizer(mapper)),
                modules, adapter);
    }

    private SchemaVersion schema(String schemaId, String version, String topic) throws Exception {
        SchemaVersion schema = new SchemaVersion();
        schema.setSchemaId(schemaId);
        schema.setVersion(version);
        schema.setTopic(topic);
        schema.setName(schemaId);
        schema.setDefinition(mapper.readTree("""
                {
                  "version":"2.0",
                  "topic":"TEST",
                  "model":"current_untyped_model",
                  "requiredQuantities":[],"optionalQuantities":[],"adjustableParameters":[],
                  "execution":{"durationSeconds":1,"stepSeconds":0.1,"durationBindings":[]},
                  "validation":{"tolerance":0.000001,"checkpointFractions":[1]},
                  "output":{"probeSeries":[]},
                  "visualization":{"scene":"fixture","series":[]}
                }
                """));
        return schema;
    }
}
