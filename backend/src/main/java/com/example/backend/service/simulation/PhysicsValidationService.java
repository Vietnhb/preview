package com.example.backend.service.simulation;

import com.example.backend.service.problem.SchemaDefinitionService;

import com.example.backend.dto.simulation.ValidationCheckpointResponse;
import com.example.backend.dto.simulation.ValidationResponse;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.reference.ReferenceSolver;
import com.example.backend.physics.reference.ReferenceSolverRegistry;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PhysicsValidationService {
    private final ReferenceSolverRegistry referenceSolvers;
    private final SchemaDefinitionService schemaDefinitions;

    public ValidationResponse validate(JsonNode specification, String schemaId, String schemaVersion,
                                       SolverOutput numerical, Map<String, Double> overrides) {
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
        return new ValidationResponse(null, passed, schemaId, tolerance, List.copyOf(checkpoints),
                List.copyOf(errors), elapsedMillis(started));
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
