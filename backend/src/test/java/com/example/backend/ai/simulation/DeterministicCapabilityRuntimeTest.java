package com.example.backend.ai.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.backend.config.properties.PhysicsRuntimeProperties;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.example.backend.physics.module.SimulationClock;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

class DeterministicCapabilityRuntimeTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final PhysicsRuntimeProperties LIMITS = new PhysicsRuntimeProperties(
            0.02, 300, 16_384, 256, 32, 512, 0.1, 40, 30, 50_000,
            262_144, 1_000, 2_000, 128, 2_000);

    @Test
    void verifiesEveryExplicitParticipantAgainstThePublishedReference() {
        AccelerationModule module = new AccelerationModule("uniform_acceleration_reference_v2");
        DeterministicCapabilityRuntime runtime = runtime(module, capability(true));

        ResultView result = execute(runtime, 2, 15, 2, 11.5);

        assertThat(result.status()).isEqualTo("VERIFIED_ANALYTICAL");
        assertThat(result.timeline().path("participants")).hasSize(2);
        assertThat(result.timeline().path("participants").get(0).path("positionSamples")).hasSize(576);
        assertThat(result.absoluteError()).isLessThan(1e-9);
        assertThat(result.timeline().path("participants").get(1).path("label").asText())
                .isEqualTo("object-2");
    }

    @Test
    void acceptsNumericalOnlyCapabilityWithoutInventingAReferencePair() {
        AccelerationModule module = new AccelerationModule(null);
        DeterministicCapabilityRuntime runtime = runtime(module, capability(false));

        ResultView result = execute(runtime, 1, 3, 2, 2);

        assertThat(result.status()).isEqualTo("VERIFIED_NUMERICAL");
        assertThat(result.referenceSolverVersion()).isNull();
        assertThat(result.verificationMethod()).isEqualTo("step_refinement_and_finite_output");
    }

    @Test
    void marksAnUnmappedDescriptionVisualOnlyAndNeverVerified() {
        SchemaDefinitionService schemas = mock(SchemaDefinitionService.class);
        when(schemas.approvedSchemas()).thenReturn(List.of());
        DeterministicCapabilityRuntime runtime = new DeterministicCapabilityRuntime(
                new PhysicsModuleRegistry(List.of(new AccelerationModule(null))), schemas, LIMITS, MAPPER);

        ObjectNode specification = MAPPER.createObjectNode();
        specification.putObject("solverBinding").put("capabilityId", "not_published");

        assertThat(runtime.execute(specification).status()).isEqualTo("VISUAL_ONLY_UNVERIFIED");
    }

    @Test
    void rejectsAReferenceThatDisagreesWithTheNumericalTrajectory() {
        AccelerationModule module = new AccelerationModule("uniform_acceleration_reference_v2");
        module.referenceOffset = 1.0;
        DeterministicCapabilityRuntime runtime = runtime(module, capability(true));

        ResultView result = execute(runtime, 1, 15, 2, 2);

        assertThat(result.status()).isEqualTo("FLAGGED");
        assertThat(result.flags()).anyMatch(flag -> flag.contains("reference mismatch"));
    }

    private DeterministicCapabilityRuntime runtime(AccelerationModule module, ObjectNode capability) {
        SchemaVersion schema = new SchemaVersion();
        schema.setSchemaId("adaptive_kinematics");
        schema.setVersion("2.0");
        schema.setDefinition(capability.get("definition"));
        SchemaDefinitionService schemas = mock(SchemaDefinitionService.class);
        when(schemas.approvedSchemas()).thenReturn(List.of(schema));
        return new DeterministicCapabilityRuntime(new PhysicsModuleRegistry(List.of(module)), schemas, LIMITS, MAPPER);
    }

    private ResultView execute(DeterministicCapabilityRuntime runtime, int count, double velocity,
            double acceleration, double duration) {
        ObjectNode specification = MAPPER.createObjectNode();
        ObjectNode binding = specification.putObject("solverBinding");
        binding.put("capabilityId", "uniform_acceleration");
        binding.put("durationSeconds", duration);
        ArrayNode participants = binding.putArray("participants");
        for (int index = 0; index < count; index++) {
            ObjectNode participant = participants.addObject();
            participant.put("id", "object-" + (index + 1));
            participant.put("label", "object-" + (index + 1));
            ObjectNode quantities = participant.putObject("quantities");
            quantities.putObject("initial_position").put("valueSI", index).put("unitSI", "m");
            quantities.putObject("initial_velocity").put("valueSI", velocity).put("unitSI", "m/s");
            quantities.putObject("acceleration").put("valueSI", acceleration).put("unitSI", "m/s2");
        }
        return ResultView.from(runtime.execute(specification));
    }

    private ObjectNode capability(boolean withReference) {
        ObjectNode definition = MAPPER.createObjectNode();
        ArrayNode capabilities = definition.putArray("capabilities");
        ObjectNode capability = capabilities.addObject();
        capability.put("capabilityId", "uniform_acceleration");
        capability.putArray("assumptions").add("constant acceleration");
        capability.putObject("validation").put("absoluteTolerance", 1e-9).put("relativeTolerance", 1e-9);
        ObjectNode execution = capability.putObject("execution");
        execution.putObject("numerical").put("solverId", "uniform_acceleration_solver_v2");
        if (withReference) execution.putObject("closedForm").put("solverId", "uniform_acceleration_reference_v2");
        else execution.putNull("closedForm");
        return MAPPER.createObjectNode().set("definition", definition);
    }

    private record ResultView(String status, List<String> flags, ObjectNode timeline, Double absoluteError,
            String referenceSolverVersion, String verificationMethod) {
        static ResultView from(DeterministicCapabilityRuntime.Result result) {
            return new ResultView(result.status(), result.flags(), result.timeline(), result.absoluteError(),
                    result.referenceSolverVersion(), result.verificationMethod());
        }
    }

    private static final class AccelerationModule implements PhysicsModule<AccelerationModule.Parameters> {
        private final String referenceId;
        private double referenceOffset;

        private AccelerationModule(String referenceId) {
            this.referenceId = referenceId;
        }

        @Override
        public String moduleId() { return "uniform_acceleration"; }

        @Override
        public String numericalSolverId() { return "uniform_acceleration_solver_v2"; }

        @Override
        public String referenceSolverId() { return referenceId; }

        @Override
        public Parameters bind(CanonicalQuantityBag quantities) {
            return new Parameters(quantities.optional("initial_position", 0),
                    quantities.optional("initial_velocity", 0), quantities.optional("acceleration", 0));
        }

        @Override
        public SolverOutput solve(Parameters parameters, SimulationClock clock) {
            List<Double> time = clock.sampleTimes();
            List<Double> position = new ArrayList<>();
            List<Double> velocity = new ArrayList<>();
            List<Double> acceleration = new ArrayList<>();
            for (double t : time) {
                position.add(parameters.x0 + parameters.v0 * t + 0.5 * parameters.a * t * t);
                velocity.add(parameters.v0 + parameters.a * t);
                acceleration.add(parameters.a);
            }
            return new SolverOutput(time, Map.of("x", position), Map.of("x", velocity),
                    Map.of("x", acceleration), Map.of());
        }

        @Override
        public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
            return new AnalyticalPoint(Map.of("x", parameters.x0 + parameters.v0 * timeSeconds
                    + 0.5 * parameters.a * timeSeconds * timeSeconds + referenceOffset));
        }

        private record Parameters(double x0, double v0, double a) { }
    }
}
