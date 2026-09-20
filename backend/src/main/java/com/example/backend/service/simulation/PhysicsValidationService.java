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
import java.util.HashMap;

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
        Map<String, OutputTolerance> outputTolerances = outputTolerances(validationDefinition, tolerance);
        List<ValidationCheckpointResponse> checkpoints = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        double duration = numerical.time().isEmpty() ? 0 : numerical.time().get(numerical.time().size() - 1);
        for (double checkpoint : checkpointsFor(validationDefinition, duration)) {
            AnalyticalPoint analytical = solver.solve(specification, overrides, checkpoint);
            if (analytical == null || analytical.values() == null || analytical.values().isEmpty()) {
                errors.add("t=" + checkpoint + " reference returned no output");
                continue;
            }
            for (Map.Entry<String, Double> expected : analytical.values().entrySet()) {
                if (expected.getKey() == null || expected.getKey().isBlank()
                        || expected.getValue() == null || !Double.isFinite(expected.getValue())) {
                    errors.add("t=" + checkpoint + " reference returned an invalid output key/value");
                    continue;
                }
                OutputTolerance contract = outputTolerances.getOrDefault(expected.getKey(),
                        new OutputTolerance(tolerance, tolerance, "numeric"));
                List<Double> numericalSeries = numerical.values().get(expected.getKey());
                double actual = interpolate(numericalSeries, numerical.time(), checkpoint);
                double absoluteError = Math.abs(actual - expected.getValue());
                double relativeError = relativeError(actual, expected.getValue());
                boolean passed = compare(actual, expected.getValue(), absoluteError, relativeError, contract);
                checkpoints.add(new ValidationCheckpointResponse(checkpoint, expected.getKey(), actual,
                        expected.getValue(), absoluteError, relativeError, Math.max(contract.absoluteTolerance(),
                                contract.relativeTolerance()), contract.absoluteTolerance(), contract.relativeTolerance(), passed));
                if (!passed) {
                    errors.add("t=" + checkpoint + " " + expected.getKey() + " expected="
                            + " computed=" + actual + " absoluteError=" + absoluteError
                            + " relativeError=" + relativeError + " absoluteTolerance="
                            + contract.absoluteTolerance() + " relativeTolerance=" + contract.relativeTolerance());
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
        if (!Double.isFinite(actual) || !Double.isFinite(expected)) return Double.POSITIVE_INFINITY;
        if (Math.abs(expected) > 1e-10) return Math.abs(actual - expected) / Math.abs(expected);
        return Math.abs(actual - expected);
    }

    private boolean compare(double actual, double expected, double absoluteError, double relativeError,
            OutputTolerance tolerance) {
        if (!Double.isFinite(actual) || !Double.isFinite(expected)) return false;
        if ("exact".equals(tolerance.comparison()) || "discrete".equals(tolerance.comparison())) {
            return Double.doubleToLongBits(actual) == Double.doubleToLongBits(expected);
        }
        return absoluteError <= tolerance.absoluteTolerance() || relativeError <= tolerance.relativeTolerance();
    }

    private Map<String, OutputTolerance> outputTolerances(JsonNode validation, double fallback) {
        Map<String, OutputTolerance> result = new HashMap<>();
        JsonNode definitions = validation.path("outputs");
        if (definitions.isObject()) {
            definitions.fields().forEachRemaining(entry -> result.put(entry.getKey(), parseTolerance(entry.getValue(), fallback)));
        } else if (definitions.isArray()) {
            for (JsonNode definition : definitions) {
                String key = definition.path("key").asText("").trim();
                if (!key.isBlank()) result.put(key, parseTolerance(definition, fallback));
            }
        }
        return Map.copyOf(result);
    }

    private OutputTolerance parseTolerance(JsonNode node, double fallback) {
        double absolute = node.path("absoluteTolerance").isNumber()
                ? node.path("absoluteTolerance").asDouble() : fallback;
        double relative = node.path("relativeTolerance").isNumber()
                ? node.path("relativeTolerance").asDouble() : fallback;
        if (!Double.isFinite(absolute) || absolute < 0 || !Double.isFinite(relative) || relative < 0) {
            throw new IllegalArgumentException("Output tolerances must be finite and non-negative");
        }
        String comparison = node.path("comparison").asText("numeric").trim().toLowerCase();
        if (!List.of("numeric", "exact", "discrete").contains(comparison)) {
            throw new IllegalArgumentException("Unsupported output comparison: " + comparison);
        }
        return new OutputTolerance(absolute, relative, comparison);
    }

    private record OutputTolerance(double absoluteTolerance, double relativeTolerance, String comparison) { }

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
