package com.example.backend.service;

import com.example.backend.dto.physics.SimulationResponse;
import com.example.backend.dto.physics.ValidationCheckpointResponse;
import com.example.backend.dto.physics.ValidationResponse;
import com.example.backend.entity.Simulation;
import com.example.backend.entity.SchemaVersion;
import com.example.backend.entity.ValidationRun;
import com.example.backend.physics.AnalyticalPoint;
import com.example.backend.physics.ReferenceSolver;
import com.example.backend.physics.ReferenceSolverRegistry;
import com.example.backend.physics.SolverOutput;
import com.example.backend.repository.ValidationRunRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PhysicsValidationService {
    private final ReferenceSolverRegistry referenceSolvers;
    private final ValidationRunRepository validationRunRepository;
    private final ObjectMapper objectMapper;
    private final SchemaDefinitionService schemaDefinitions;

    public ValidationResponse validate(JsonNode specification, String schemaId, String schemaVersion,
                                      SolverOutput numerical, Map<String, Double> overrides,
                                      Simulation simulation) {
        long started = System.nanoTime();
        ReferenceSolver solver = referenceSolvers.get(
                schemaDefinitions.requireSolverBinding(schemaId, schemaVersion).referenceSolverId());
        SchemaVersion schema = schemaDefinitions.requireApproved(schemaId, schemaVersion);
        JsonNode validationDefinition = schema.getDefinition().path("validation");
        double tolerance = validationDefinition.path("tolerance").asDouble();
        List<ValidationCheckpointResponse> checkpoints = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        double duration = numerical.time().isEmpty() ? 0 : numerical.time().get(numerical.time().size() - 1);
        for (double checkpoint : checkpointsFor(validationDefinition, duration)) {
            AnalyticalPoint analytical = solver.solve(specification, overrides, checkpoint);
            for (Map.Entry<String, Double> expected : analytical.values().entrySet()) {
                double actual = interpolate(numerical.values().get(expected.getKey()), numerical.time(), checkpoint);
                double error = relativeError(actual, expected.getValue());
                boolean passed = error <= tolerance;
                checkpoints.add(new ValidationCheckpointResponse(checkpoint, expected.getKey(), actual,
                        expected.getValue(), error, tolerance, passed));
                if (!passed) {
                    errors.add("t=" + checkpoint + " " + expected.getKey() + " expected="
                            + expected.getValue() + " computed=" + actual + " relativeError=" + error);
                }
            }
        }
        boolean passed = errors.isEmpty();
        UUID validationRunId = null;
        if (simulation != null) {
            ValidationRun run = new ValidationRun();
            run.setSimulation(simulation);
            run.setStatus(passed ? "PASS" : "FAIL");
            run.setPassed(passed);
            run.setCheckpoints(objectMapper.valueToTree(checkpoints));
            run.setErrorMessage(errors.isEmpty() ? null : String.join("; ", errors));
            validationRunId = validationRunRepository.save(run).getId();
        }
        return new ValidationResponse(validationRunId, passed, schemaId, tolerance, List.copyOf(checkpoints),
                List.copyOf(errors), elapsedMillis(started));
    }

    public ValidationResponse validateWithoutPersistence(JsonNode specification, String schemaId,
                                                          SolverOutput numerical,
                                                          Map<String, Double> overrides) {
        SchemaVersion schema = schemaDefinitions.requireApproved(schemaId);
        return validate(specification, schemaId, schema.getVersion(), numerical, overrides, null);
    }

    private double interpolate(List<Double> values, List<Double> times, double target) {
        if (values == null || values.isEmpty() || times.isEmpty()) return Double.NaN;
        if (target <= times.get(0)) return values.get(0);
        for (int i = 1; i < times.size(); i++) {
            if (target <= times.get(i)) {
                double beforeTime = times.get(i - 1);
                double ratio = (target - beforeTime) / (times.get(i) - beforeTime);
                return values.get(i - 1) + ratio * (values.get(i) - values.get(i - 1));
            }
        }
        return values.get(values.size() - 1);
    }

    private double relativeError(double actual, double expected) {
        if (Math.abs(expected) > 1e-10) return Math.abs(actual - expected) / Math.abs(expected);
        return Math.abs(actual - expected);
    }

    private List<Double> checkpointsFor(JsonNode definition, double duration) {
        double end = Math.max(0.01, duration);
        List<Double> result = new ArrayList<>();
        for (JsonNode fraction : definition.path("checkpointFractions")) result.add(end * fraction.asDouble());
        if (result.isEmpty()) throw new IllegalStateException("Schema validation checkpoints are missing");
        return List.copyOf(result);
    }

    private double elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000.0;
    }
}
