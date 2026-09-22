package com.example.backend.service.simulation;

import com.example.backend.ai.normalization.UnitNormalizer;
import com.example.backend.dto.simulation.SimulationRequest;
import com.example.backend.entity.account.User;
import com.example.backend.entity.enums.ConfirmationState;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.entity.problem.Specification;
import com.example.backend.entity.simulation.Simulation;
import com.example.backend.entity.simulation.SimulationRun;
import com.example.backend.physics.binding.CanonicalQuantityCompiler;
import com.example.backend.physics.compatibility.LegacyPhysicsExecutionAdapterV1;
import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.physics.module.modern.RadiationSafetyModule;
import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolverRegistry;
import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolverRegistry;
import com.example.backend.repository.library.LibraryItemRepository;
import com.example.backend.repository.problem.SpecificationRepository;
import com.example.backend.repository.simulation.SimulationRepository;
import com.example.backend.repository.simulation.SimulationRunRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimulationServiceScalarPersistenceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void persistsAndReplaysScalarOutputsWithoutSeriesExpansion() throws Exception {
        UUID specificationId = UUID.randomUUID();
        UUID simulationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        User user = new User();
        Specification specification = new Specification();
        specification.setId(specificationId);
        specification.setSchemaId("radiation_safety");
        specification.setSchemaVersion("1.1");
        specification.setConfirmationState(ConfirmationState.CONFIRMED);
        com.example.backend.simulation.assets.AssetSelectionFixtures.approve(specification, mapper);

        JsonNode definition = mapper.readTree("""
                {
                  "version":"1.1", "topic":"MODERN_PHYSICS", "model":"radiation_safety",
                  "requiredQuantities":[
                    {"key":"reference_dose_rate","allowedUnits":["Gy/s"],"nonNegative":true},
                    {"key":"reference_distance","allowedUnits":["m"],"positive":true},
                    {"key":"distance","allowedUnits":["m"],"positive":true}
                  ],
                  "optionalQuantities":[], "adjustableParameters":[],
                  "execution":{"durationSeconds":1,"stepSeconds":0.25},
                  "validation":{"tolerance":1e-12,"checkpointFractions":[0.25,0.5,0.75,1]},
                  "output":{"definitions":[
                    {"key":"doseRate","kind":"scalar","unit":"Gy/s","required":true}
                  ]}
                }
                """);
        SchemaVersion schema = new SchemaVersion();
        schema.setSchemaId("radiation_safety");
        schema.setVersion("1.1");
        schema.setDefinition(definition);
        var compiled = new SchemaCompiler(mapper).compile(definition, "radiation_safety");
        JsonNode input = mapper.readTree("""
                {
                  "schemaId":"radiation_safety", "schemaVersion":"1.1", "model":"radiation_safety",
                  "quantities":[
                    {"name":"reference_dose_rate","value":4,"originalUnit":"Gy/s"},
                    {"name":"reference_distance","value":1,"originalUnit":"m"},
                    {"name":"distance","value":2,"originalUnit":"m"}
                  ], "relations":[]
                }
                """);

        SimulationRepository simulations = mock(SimulationRepository.class);
        LibraryItemRepository libraries = mock(LibraryItemRepository.class);
        SimulationRunRepository runs = mock(SimulationRunRepository.class);
        SpecificationRepository specifications = mock(SpecificationRepository.class);
        PhysicsSolverRegistry legacySolvers = mock(PhysicsSolverRegistry.class);
        SchemaDefinitionService definitions = mock(SchemaDefinitionService.class);
        SpecificationReadinessService readiness = mock(SpecificationReadinessService.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        var binding = new SchemaDefinitionService.SolverBinding(
                RadiationSafetyModule.NUMERICAL_SOLVER_ID, RadiationSafetyModule.REFERENCE_SOLVER_ID, "1.1");

        when(currentUser.requireCurrentUser()).thenReturn(user);
        when(specifications.findByIdAndSubmissionOwner(specificationId, user)).thenReturn(Optional.of(specification));
        when(readiness.blockers(specification)).thenReturn(List.of());
        when(readiness.toJson(specification)).thenReturn(input);
        when(definitions.requireCurrentApproved("radiation_safety", "1.1")).thenReturn(schema);
        when(definitions.requirePublishedVersion("radiation_safety", "1.1")).thenReturn(schema);
        when(definitions.requireSolverBinding("radiation_safety", "1.1")).thenReturn(binding);
        when(definitions.effectiveAdjustments(eq(input), eq(definition), eq(Map.of()), eq(Map.of())))
                .thenReturn(Map.of());
        when(definitions.durationSeconds(input, definition)).thenReturn(1.0);
        when(definitions.compiled(schema)).thenReturn(compiled);
        when(definitions.visualization(definition)).thenReturn(mapper.createObjectNode());
        when(simulations.save(any(Simulation.class))).thenAnswer(invocation -> {
            Simulation saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(simulationId);
            return saved;
        });
        when(runs.save(any(SimulationRun.class))).thenAnswer(invocation -> {
            SimulationRun saved = invocation.getArgument(0);
            saved.setId(runId);
            return saved;
        });
        when(specifications.save(any(Specification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var adapter = new LegacyPhysicsExecutionAdapterV1(List.of());
        var validation = new PhysicsValidationService(new ReferenceSolverRegistry(List.of()), definitions, adapter);
        var service = new SimulationService(simulations, libraries, runs, specifications, legacySolvers,
                validation, currentUser, mapper, definitions, readiness,
                new CanonicalQuantityCompiler(new UnitNormalizer(mapper)),
                new PhysicsModuleRegistry(List.of(new RadiationSafetyModule())), adapter);

        var response = service.run(new SimulationRequest(specificationId, Map.of()));
        var captor = org.mockito.ArgumentCaptor.forClass(SimulationRun.class);
        verify(runs).save(captor.capture());
        SimulationRun persisted = captor.getValue();

        assertTrue(response.success());
        assertEquals(Map.of("doseRate", 1.0), response.scalarOutputs());
        assertEquals(List.of(1.0, 1.0, 1.0, 1.0, 1.0), response.values().get("doseRate"));
        assertEquals(5, response.time().size());
        assertEquals(1.0, persisted.getDurationSeconds(), 0.0);
        assertEquals("radiation_safety", persisted.getSchemaId());
        assertEquals("1.1", persisted.getSchemaVersion());
        assertEquals("1.1", persisted.getBindingVersion());
        assertEquals(RadiationSafetyModule.NUMERICAL_SOLVER_ID, persisted.getNumericalSolverId());
        assertEquals(RadiationSafetyModule.REFERENCE_SOLVER_ID, persisted.getReferenceSolverId());
        assertEquals("1.1", persisted.getOutputContractVersion());
        assertEquals(compiled.checksum(), persisted.getOutputContractChecksum());
        assertEquals(1.0, persisted.getResult().path("scalarOutputs").path("doseRate").asDouble(), 0.0);
        assertTrue(persisted.getResult().path("values").isEmpty());

        when(runs.findById(runId)).thenReturn(Optional.of(persisted));
        var replay = service.replay(persisted.getSimulation(), runId);
        assertEquals(response.visualization(), replay.visualization());
        assertEquals(Map.of("doseRate", 1.0), replay.scalarOutputs());
        assertEquals(List.of(1.0, 1.0, 1.0, 1.0, 1.0), replay.values().get("doseRate"));
        assertEquals(1.0, replay.rawResult().path("scalarOutputs").path("doseRate").asDouble(), 0.0);
        verify(legacySolvers, never()).get(anyString());
    }
}
