package com.example.backend.service.simulation;

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
import com.example.backend.physics.module.circuits.AcWaveformModule;
import com.example.backend.physics.reference.ReferenceSolverRegistry;
import com.example.backend.physics.solver.PhysicsSolverRegistry;
import com.example.backend.repository.library.LibraryItemRepository;
import com.example.backend.repository.problem.SpecificationRepository;
import com.example.backend.repository.simulation.SimulationRepository;
import com.example.backend.repository.simulation.SimulationRunRepository;
import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.problem.CompiledSchema;
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

/** Exercises the persisted simulation path through schema-bound typed physics runtime. */
class SimulationServiceTypedPathTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void runCompilesPinnedQuantitiesSolvesValidatesAndPersistsTypedResult() throws Exception {
        UUID specificationId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        User user = new User();
        Specification specification = new Specification();
        specification.setId(specificationId);
        specification.setSchemaId("ac_waveform");
        specification.setSchemaVersion("1.0");
        specification.setConfirmationState(ConfirmationState.CONFIRMED);

        SchemaVersion schema = new SchemaVersion();
        schema.setSchemaId("ac_waveform");
        schema.setVersion("1.0");
        schema.setDefinition(mapper.readTree("""
                {
                  "version":"1.0","topic":"CIRCUITS","model":"ac_waveform",
                  "requiredQuantities":[
                    {"key":"peak_voltage","aliases":["V0"],"allowedUnits":["V"],"nonNegative":true},
                    {"key":"frequency","aliases":["f"],"allowedUnits":["Hz"],"positive":true}
                  ],
                  "optionalQuantities":[
                    {"key":"phase","aliases":["phi"],"allowedUnits":["rad"],"defaultValue":0}
                  ],
                  "execution":{"durationSeconds":1,"stepSeconds":0.25},
                  "validation":{"tolerance":1e-12,"checkpointFractions":[0.25,0.5,0.75,1]},
                  "output":{"type":"timeseries","probeSeries":["voltage","rmsVoltage"]},
                  "visualization":{"series":[
                    {"source":"values.voltage","unit":"V"},
                    {"source":"values.rmsVoltage","unit":"V"}
                  ]}
                }
                """));
        CompiledSchema compiled = new SchemaCompiler(mapper).compile(schema.getDefinition(), schema.getSchemaId());
        JsonNode input = mapper.readTree("""
                {
                  "schemaId":"ac_waveform","schemaVersion":"1.0","model":"ac_waveform",
                  "quantities":[
                    {"name":"peak_voltage","value":12,"originalUnit":"V"},
                    {"name":"frequency","value":0.25,"originalUnit":"Hz"}
                  ],
                  "relations":[]
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
        var bindings = new SchemaDefinitionService.SolverBinding(
                "ac_waveform_solver", "ac_waveform_reference", "1.0");
        when(currentUser.requireCurrentUser()).thenReturn(user);
        when(specifications.findByIdAndSubmissionOwner(specificationId, user)).thenReturn(Optional.of(specification));
        when(readiness.blockers(specification)).thenReturn(List.of());
        when(readiness.toJson(specification)).thenReturn(input);
        when(definitions.requireCurrentApproved("ac_waveform", "1.0")).thenReturn(schema);
        when(definitions.requirePublishedVersion("ac_waveform", "1.0")).thenReturn(schema);
        when(definitions.requireSolverBinding("ac_waveform", "1.0")).thenReturn(bindings);
        when(definitions.effectiveAdjustments(eq(input), eq(schema.getDefinition()), eq(Map.of()), eq(Map.of())))
                .thenReturn(Map.of());
        when(definitions.durationSeconds(input, schema.getDefinition())).thenReturn(1.0);
        when(definitions.compiled(schema)).thenReturn(compiled);
        when(definitions.visualization(schema.getDefinition())).thenReturn(mapper.createObjectNode());
        when(simulations.save(any(Simulation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(runs.save(any(SimulationRun.class))).thenAnswer(invocation -> {
            SimulationRun saved = invocation.getArgument(0);
            saved.setId(runId);
            return saved;
        });
        when(specifications.save(any(Specification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var validation = new PhysicsValidationService(new ReferenceSolverRegistry(List.of()), definitions,
                new LegacyPhysicsExecutionAdapterV1(List.of()));
        var service = new SimulationService(simulations, libraries, runs, specifications,
                legacySolvers, validation, currentUser, mapper, definitions, readiness,
                new CanonicalQuantityCompiler(new com.example.backend.ai.normalization.UnitNormalizer(mapper)),
                new PhysicsModuleRegistry(List.of(new AcWaveformModule())),
                new LegacyPhysicsExecutionAdapterV1(List.of()));

        var response = service.run(new SimulationRequest(specificationId, Map.of()));

        assertTrue(response.success());
        assertTrue(response.validationPassed());
        assertEquals(runId, response.simulationRunId());
        assertEquals(5, response.time().size());
        assertEquals(12.0 / Math.sqrt(2.0), response.values().get("rmsVoltage").getLast(), 1e-12);
        assertEquals(8, response.validation().checkpoints().size());
        assertEquals(4L, response.validation().checkpoints().stream()
                .map(com.example.backend.dto.simulation.ValidationCheckpointResponse::time)
                .distinct().count());
        assertEquals("time_limit", response.rawResult().path("resolvedEnd").path("reason").asText());
        verify(runs).save(any(SimulationRun.class));
        verify(legacySolvers, never()).get(anyString());
    }
}
