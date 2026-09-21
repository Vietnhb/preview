package com.example.backend.service.simulation;

import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.module.BoundPhysicsModule;
import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.physics.module.SimulationClock;
import com.example.backend.physics.module.circuits.AcWaveformModule;
import com.example.backend.physics.module.kinematics.KinematicsProjectileModule;
import com.example.backend.physics.compatibility.LegacyPhysicsExecutionAdapterV1;
import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolverRegistry;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.example.backend.service.problem.CompiledSchema;
import com.example.backend.physics.output.PhysicsOutput;
import com.example.backend.physics.output.PhysicsOutputContract;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PhysicsValidationServiceTypedModuleTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void validatesWithPinnedTypedReferenceWithoutCallingLegacyReferenceRegistry() throws Exception {
        SchemaDefinitionService schemaDefinitions = mock(SchemaDefinitionService.class);
        when(schemaDefinitions.requireSolverBinding("ac_waveform", "1.0"))
                .thenReturn(new SchemaDefinitionService.SolverBinding(
                        AcWaveformModule.NUMERICAL_SOLVER_ID, AcWaveformModule.REFERENCE_SOLVER_ID, "1"));
        SchemaVersion schema = new SchemaVersion();
        schema.setSchemaId("ac_waveform");
        schema.setVersion("1.0");
        schema.setTopic("CIRCUITS");
        schema.setDefinition(objectMapper.readTree("""
                {
                  "validation": {
                    "tolerance": 0.000001,
                    "checkpointFractions": [0.5]
                  }
                }
                """));
        when(schemaDefinitions.requirePublishedVersion("ac_waveform", "1.0")).thenReturn(schema);
        when(schemaDefinitions.compiled(schema)).thenReturn(new CompiledSchema("ac_waveform", "1.0", "CIRCUITS",
                "ac_waveform", Map.of(), Map.of(), Map.of(), Set.of("voltage", "rmsVoltage"), Map.of(),
                new CompiledSchema.ExecutionDefinition(1, 0.5),
                new CompiledSchema.ValidationDefinition(0.000001, List.of(0.5)), "test-checksum"));

        CanonicalQuantityBag quantities = new CanonicalQuantityBag(
                Map.of("peak_voltage", new BigDecimal("10"), "frequency", new BigDecimal("0.25")),
                Map.of("peak_voltage", "V", "frequency", "Hz"));
        BoundPhysicsModule typedModule = new PhysicsModuleRegistry(List.of(new AcWaveformModule())).bind(
                AcWaveformModule.NUMERICAL_SOLVER_ID, AcWaveformModule.REFERENCE_SOLVER_ID, quantities);
        var numerical = typedModule.solve(new SimulationClock(1, 0.5));
        var typed = typedModule.solve(new PhysicsOutputContract("ac_waveform", "1.0", "ac_waveform",
                Map.of("voltage", new PhysicsOutputContract.OutputDefinition(
                                PhysicsOutput.OutputKind.TIME_SERIES, "V", true),
                        "rmsVoltage", new PhysicsOutputContract.OutputDefinition(
                                PhysicsOutput.OutputKind.TIME_SERIES, "V", true)), 100),
                new SimulationClock(1, 0.5));
        assertEquals(Set.of("voltage", "rmsVoltage"), typed.typed().outputs().stream()
                .map(PhysicsOutput::key).collect(Collectors.toSet()));
        assertEquals(3, typed.typed().timeSeconds().size());

        // The empty registry would reject lookup; success proves the pinned module's
        // independent reference operation handled every validation checkpoint.
        PhysicsValidationService service = new PhysicsValidationService(
                new ReferenceSolverRegistry(List.of()), schemaDefinitions,
                new LegacyPhysicsExecutionAdapterV1(List.of()));
        var validation = service.validateTyped(objectMapper.readTree("{}"), "ac_waveform", "1.0",
                typed.typed(), Map.of(), typedModule);

        assertTrue(validation.passed(), () -> String.join("; ", validation.errors()));
        assertEquals(2, validation.checkpoints().size());
        assertEquals(Set.of("voltage", "rmsVoltage"), validation.checkpoints().stream()
                .map(checkpoint -> checkpoint.quantity()).collect(Collectors.toSet()));
        assertEquals(0.5, validation.checkpoints().get(0).time(), 1e-12);
    }

    @Test
    void detectsPerturbedTypedProjectileOutputAgainstIndependentReference() throws Exception {
        SchemaDefinitionService schemaDefinitions = mock(SchemaDefinitionService.class);
        when(schemaDefinitions.requireSolverBinding("kinematics_projectile", "1.0"))
                .thenReturn(new SchemaDefinitionService.SolverBinding(
                        KinematicsProjectileModule.NUMERICAL_SOLVER_ID,
                        KinematicsProjectileModule.REFERENCE_SOLVER_ID, "1"));
        SchemaVersion schema = new SchemaVersion();
        schema.setSchemaId("kinematics_projectile");
        schema.setVersion("1.0");
        schema.setTopic("KINEMATICS");
        schema.setDefinition(objectMapper.readTree("""
                {
                  "validation": {
                    "tolerance": 0.000001,
                    "checkpointFractions": [0.5]
                  }
                }
                """));
        when(schemaDefinitions.requirePublishedVersion("kinematics_projectile", "1.0")).thenReturn(schema);
        when(schemaDefinitions.compiled(schema)).thenReturn(new CompiledSchema(
                "kinematics_projectile", "1.0", "KINEMATICS", "kinematics_projectile",
                Map.of(), Map.of(), Map.of(),
                Set.of("x", "displacement", "y", "vx", "vy", "ax", "ay"), Map.of(),
                new CompiledSchema.ExecutionDefinition(1, 0.5),
                new CompiledSchema.ValidationDefinition(0.000001, List.of(0.5)), "test-checksum"));

        CanonicalQuantityBag quantities = new CanonicalQuantityBag(
                Map.of("initial_position", new BigDecimal("0"),
                        "initial_height", new BigDecimal("0"),
                        "initial_velocity", new BigDecimal("10"),
                        "launch_angle", BigDecimal.valueOf(Math.PI / 2.0),
                        "gravitational_acceleration", new BigDecimal("9.81")),
                Map.of("initial_position", "m", "initial_height", "m", "initial_velocity", "m/s",
                        "launch_angle", "rad", "gravitational_acceleration", "m/s2"));
        BoundPhysicsModule typedModule = new PhysicsModuleRegistry(List.of(new KinematicsProjectileModule())).bind(
                KinematicsProjectileModule.NUMERICAL_SOLVER_ID,
                KinematicsProjectileModule.REFERENCE_SOLVER_ID, quantities);
        var numerical = typedModule.solve(new SimulationClock(1, 0.5));
        PhysicsValidationService service = new PhysicsValidationService(
                new ReferenceSolverRegistry(List.of()), schemaDefinitions,
                new LegacyPhysicsExecutionAdapterV1(List.of()));

        // Establish that the production module's numerical and independent reference paths agree first.
        var baseline = service.validate(objectMapper.readTree("{}"), "kinematics_projectile", "1.0",
                numerical, Map.of(), typedModule);
        assertTrue(baseline.passed(), () -> String.join("; ", baseline.errors()));

        // Fault injection: shift only the numerical y series by 2 m. The typed reference remains pinned
        // to the module's separate endpoint-average oracle and must reject the corrupted result.
        Map<String, List<Double>> corruptedValues = new LinkedHashMap<>(numerical.values());
        corruptedValues.put("y", numerical.values().get("y").stream().map(value -> value + 2.0).toList());
        var corrupted = new com.example.backend.physics.model.SolverOutput(
                numerical.time(), numerical.positions(), numerical.velocities(), numerical.accelerations(),
                corruptedValues, numerical.scalarFields(), numerical.scalarOutputs());
        var validation = service.validate(objectMapper.readTree("{}"), "kinematics_projectile", "1.0",
                corrupted, Map.of(), typedModule);

        var yCheckpoint = validation.checkpoints().stream()
                .filter(checkpoint -> checkpoint.quantity().equals("y"))
                .findFirst().orElseThrow();
        assertFalse(validation.passed(), "the independent reference must reject the injected numerical fault");
        assertFalse(yCheckpoint.passed());
        assertEquals(5.77375, yCheckpoint.numerical(), 1e-9);
        assertEquals(3.77375, yCheckpoint.analytical(), 1e-9);
        assertTrue(validation.errors().stream().anyMatch(error -> error.contains(" y expected=")));
    }
}
