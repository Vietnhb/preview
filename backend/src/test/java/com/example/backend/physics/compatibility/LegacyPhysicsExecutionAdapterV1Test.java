package com.example.backend.physics.compatibility;

import com.example.backend.exception.SolverBindingException;
import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolver;
import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolverRegistry;
import com.example.backend.physics.compatibility.legacy.reference.kinematics.KinematicsReferenceSolver;
import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;
import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolverRegistry;
import com.example.backend.physics.compatibility.legacy.solver.kinematics.KinematicsSolver;
import com.example.backend.physics.compatibility.legacy.model.kinematics.KinematicsParameters;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LegacyPhysicsExecutionAdapterV1Test {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void currentSchemaWithoutTypedModuleFailsBeforeLegacyRegistryLookupOrCall() {
        var pin = new LegacyPhysicsExecutionAdapterV1.PinnedExecution(
                "current-model", "2.0", "2.0", "legacy-numerical", "legacy-reference");
        PhysicsSolverRegistry registry = mock(PhysicsSolverRegistry.class);
        var adapter = new LegacyPhysicsExecutionAdapterV1(List.of());

        SolverBindingException failure = assertThrows(SolverBindingException.class,
                () -> adapter.authorizeNumerical(LegacyPhysicsExecutionAdapterV1.VERSION, pin, true, registry));

        assertTrue(failure.getMessage().contains("Latest approved schema"));
        verify(registry, never()).get(any());
    }

    @Test
    void exactHistoricalPermitAuthorizesOnlyItsPinnedNumericalPair() throws Exception {
        var pin = new LegacyPhysicsExecutionAdapterV1.PinnedExecution(
                "historical-model", "1.0", "solver-binding-7", "historical-numerical", "historical-reference");
        var permit = new LegacyPhysicsExecutionAdapterV1.Permit(
                "historical-model", "1.0", "solver-binding-7", "historical-numerical", "historical-reference");
        PhysicsSolver solver = mock(PhysicsSolver.class);
        SolverOutput expected = new SolverOutput(List.of(0.0), Map.of(), Map.of(), Map.of(), Map.of());
        when(solver.solverId()).thenReturn("historical-numerical");
        when(solver.solve(any(), eq(Map.of()), anyDouble(), anyDouble())).thenReturn(expected);
        PhysicsSolverRegistry registry = mock(PhysicsSolverRegistry.class);
        when(registry.get("historical-numerical")).thenReturn(solver);
        var adapter = new LegacyPhysicsExecutionAdapterV1(List.of(permit));

        var authorized = adapter.authorizeNumerical(LegacyPhysicsExecutionAdapterV1.VERSION,
                pin, false, registry);
        SolverOutput actual = authorized.solve(mapper.readTree("{}"), Map.of(), 1.0, 0.1);

        assertSame(expected, actual);
        verify(registry).get("historical-numerical");
        verify(solver).solve(any(), eq(Map.of()), eq(1.0), eq(0.1));
    }

    @Test
    void exactHistoricalPermitAlsoAuthorizesOnlyItsPinnedReferencePair() throws Exception {
        var pin = new LegacyPhysicsExecutionAdapterV1.PinnedExecution(
                "historical-model", "1.0", "solver-binding-7", "historical-numerical", "historical-reference");
        var permit = new LegacyPhysicsExecutionAdapterV1.Permit(
                "historical-model", "1.0", "solver-binding-7", "historical-numerical", "historical-reference");
        ReferenceSolver solver = mock(ReferenceSolver.class);
        AnalyticalPoint expected = new AnalyticalPoint(Map.of("position", 2.5));
        when(solver.solverId()).thenReturn("historical-reference");
        when(solver.solve(any(), eq(Map.of()), anyDouble())).thenReturn(expected);
        ReferenceSolverRegistry registry = mock(ReferenceSolverRegistry.class);
        when(registry.get("historical-reference")).thenReturn(solver);
        var adapter = new LegacyPhysicsExecutionAdapterV1(List.of(permit));

        var authorized = adapter.authorizeReference(LegacyPhysicsExecutionAdapterV1.VERSION,
                pin, false, registry);
        AnalyticalPoint actual = authorized.solve(mapper.readTree("{}"), Map.of(), 0.5);

        assertSame(expected, actual);
        verify(registry).get("historical-reference");
        verify(solver).solve(any(), eq(Map.of()), eq(0.5));
    }

    @Test
    void rejectsWrongAdapterVersionAndAnyChangedBindingFieldBeforeLookup() {
        var permit = new LegacyPhysicsExecutionAdapterV1.Permit(
                "historical-model", "1.0", "solver-binding-7", "historical-numerical", "historical-reference");
        var adapter = new LegacyPhysicsExecutionAdapterV1(List.of(permit));
        PhysicsSolverRegistry registry = mock(PhysicsSolverRegistry.class);
        var exact = new LegacyPhysicsExecutionAdapterV1.PinnedExecution(
                "historical-model", "1.0", "solver-binding-7", "historical-numerical", "historical-reference");
        var changedBinding = new LegacyPhysicsExecutionAdapterV1.PinnedExecution(
                "historical-model", "1.0", "solver-binding-8", "historical-numerical", "historical-reference");
        var changedPair = new LegacyPhysicsExecutionAdapterV1.PinnedExecution(
                "historical-model", "1.0", "solver-binding-7", "different-numerical", "historical-reference");
        var changedIdentity = new LegacyPhysicsExecutionAdapterV1.PinnedExecution(
                "other-historical-model", "1.0", "solver-binding-7", "historical-numerical", "historical-reference");

        assertThrows(SolverBindingException.class,
                () -> adapter.authorizeNumerical("legacy-physics-execution-v999", exact, false, registry));
        assertThrows(SolverBindingException.class,
                () -> adapter.authorizeNumerical(LegacyPhysicsExecutionAdapterV1.VERSION,
                        changedBinding, false, registry));
        assertThrows(SolverBindingException.class,
                () -> adapter.authorizeNumerical(LegacyPhysicsExecutionAdapterV1.VERSION,
                        changedPair, false, registry));
        assertThrows(SolverBindingException.class,
                () -> adapter.authorizeNumerical(LegacyPhysicsExecutionAdapterV1.VERSION,
                        changedIdentity, false, registry));
        assertThrows(SolverBindingException.class,
                () -> adapter.authorizeNumerical(LegacyPhysicsExecutionAdapterV1.VERSION,
                        exact, true, registry));

        verify(registry, never()).get(any());
    }

    @Test
    void historyDataGrantsOnlyNonLatestActiveAndArchivedVersions() {
        var adapter = new LegacyPhysicsExecutionAdapterV1(mapper);

        assertTrue(adapter.permits(new LegacyPhysicsExecutionAdapterV1.PinnedExecution(
                "kinematics", "1.9", "1.9", "kinematics_solver", "kinematics_reference")));
        assertTrue(adapter.permits(new LegacyPhysicsExecutionAdapterV1.PinnedExecution(
                "kinematics", "1.8", "1.8", "kinematics_solver", "kinematics_reference")));
        assertTrue(adapter.permits(new LegacyPhysicsExecutionAdapterV1.PinnedExecution(
                "kinematics", "1.10", "1.10", "uniform_acceleration_solver_v2",
                "uniform_acceleration_reference_v2")));
        assertFalse(adapter.permits(new LegacyPhysicsExecutionAdapterV1.PinnedExecution(
                "kinematics", "1.11", "1.11", "uniform_acceleration_solver_v2",
                "uniform_acceleration_reference_v2")));
    }

    @Test
    void historicalPermitExecutesConcreteNumericalAndReferencePairWithoutOpeningLatestRouting() throws Exception {
        var pin = new LegacyPhysicsExecutionAdapterV1.PinnedExecution(
                "kinematics", "1.9", "1.9", "kinematics_solver", "kinematics_reference");
        var adapter = new LegacyPhysicsExecutionAdapterV1(List.of(new LegacyPhysicsExecutionAdapterV1.Permit(
                "kinematics", "1.9", "1.9", "kinematics_solver", "kinematics_reference")));
        var numerical = adapter.authorizeNumerical(LegacyPhysicsExecutionAdapterV1.VERSION, pin, false,
                new PhysicsSolverRegistry(List.of(new KinematicsSolver())));
        var reference = adapter.authorizeReference(LegacyPhysicsExecutionAdapterV1.VERSION, pin, false,
                new ReferenceSolverRegistry(List.of(new KinematicsReferenceSolver())));
        JsonNode specification = mapper.readTree("""
                {"model":"projectile","initial_position":0,"initial_velocity":10,
                 "initial_height":1,"launch_angle":0,"gravitational_acceleration":10}
                """);
        KinematicsParameters parsed = KinematicsParameters.from(specification, Map.of());
        assertEquals(1.0, parsed.initialHeight(), 1e-9);
        assertEquals(10.0, parsed.gravity(), 1e-9);

        SolverOutput output = numerical.solve(specification, Map.of(), 1.0, 0.5);
        AnalyticalPoint point = reference.solve(specification, Map.of(), 1.0);

        assertEquals(List.of(0.0, 0.2, 0.4, 0.6000000000000001, 0.8, 1.0), output.time());
        assertEquals(10.0, output.values().get("x").getLast(), 1e-9);
        assertEquals(-10.0, point.values().get("vy"), 1e-9);
        assertEquals(-4.0, point.values().get("y"), 1e-9);
    }
}
