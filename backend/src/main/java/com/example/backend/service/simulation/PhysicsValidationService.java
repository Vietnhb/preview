package com.example.backend.service.simulation;

import com.example.backend.service.problem.SchemaDefinitionService;
import com.example.backend.service.problem.CompiledSchema;
import com.example.backend.exception.SolverBindingException;
import com.example.backend.physics.compatibility.LegacyPhysicsExecutionAdapterV1;

import com.example.backend.dto.simulation.ValidationCheckpointResponse;
import com.example.backend.dto.simulation.ValidationResponse;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.reference.ReferenceSolver;
import com.example.backend.physics.reference.ReferenceSolverRegistry;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.BoundPhysicsModule;
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
    private final LegacyPhysicsExecutionAdapterV1 legacyPhysicsExecution;

    public ValidationResponse validate(JsonNode specification, String schemaId, String schemaVersion,
                                       SolverOutput numerical, Map<String, Double> overrides) {
        return validate(specification, schemaId, schemaVersion, numerical, overrides, null);
    }

    public ValidationResponse validate(JsonNode specification, String schemaId, String schemaVersion,
                                       SolverOutput numerical, Map<String, Double> overrides,
                                       BoundPhysicsModule typedModule) {
        long started = System.nanoTime();
        SchemaVersion schema = schemaDefinitions.requirePublishedVersion(schemaId, schemaVersion);
        SchemaDefinitionService.SolverBinding binding = schemaDefinitions.requireSolverBinding(schema.getSchemaId(), schemaVersion);
        if (typedModule != null && (!typedModule.numericalSolverId().equals(binding.numericalSolverId())
                || !typedModule.referenceSolverId().equals(binding.referenceSolverId()))) {
            throw new SolverBindingException("Typed physics module does not match pinned solver binding for "
                    + schemaId + "@" + schemaVersion);
        }
        LegacyPhysicsExecutionAdapterV1.AuthorizedReferenceSolver legacySolver = null;
        if (typedModule == null) {
            boolean latestApproved = schemaDefinitions.isLatestApprovedEnabledVersion(
                    schema.getSchemaId(), schema.getVersion());
            var pinned = new LegacyPhysicsExecutionAdapterV1.PinnedExecution(
                    schema.getSchemaId(), schema.getVersion(), binding.version(),
                    binding.numericalSolverId(), binding.referenceSolverId());
            legacySolver = legacyPhysicsExecution.authorizeReference(
                    LegacyPhysicsExecutionAdapterV1.VERSION, pinned, latestApproved, referenceSolvers);
        }
        CompiledSchema.ValidationDefinition validationDefinition = schemaDefinitions.compiled(schema).validation();
        double tolerance = validationDefinition.tolerance();
        Map<String, CompiledSchema.OutputValidationDefinition> outputTolerances =
                validationDefinition.outputTolerances();
        List<ValidationCheckpointResponse> checkpoints = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        double duration = numerical.time().isEmpty() ? 0 : numerical.time().get(numerical.time().size() - 1);
        for (double checkpoint : checkpointsFor(validationDefinition, duration)) {
            AnalyticalPoint analytical = typedModule == null
                    ? legacySolver.solve(specification, overrides, checkpoint)
                    : typedModule.reference(checkpoint);
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
                CompiledSchema.OutputValidationDefinition contract = outputTolerances.getOrDefault(expected.getKey(),
                        new CompiledSchema.OutputValidationDefinition(tolerance, tolerance, "numeric"));
                Double scalarActual = numerical.scalarOutputs().get(expected.getKey());
                List<Double> numericalSeries = numerical.values().get(expected.getKey());
                double actual = scalarActual != null ? scalarActual
                        : interpolate(numericalSeries, numerical.time(), checkpoint);
                double absoluteError = Math.abs(actual - expected.getValue());
                double relativeError = relativeError(actual, expected.getValue());
                boolean passed = compare(actual, expected.getValue(), absoluteError, relativeError, contract);
                checkpoints.add(new ValidationCheckpointResponse(checkpoint, expected.getKey(), actual,
                        expected.getValue(), absoluteError, relativeError, Math.max(contract.absoluteTolerance(),
                                contract.relativeTolerance()), contract.absoluteTolerance(), contract.relativeTolerance(), passed));
                if (!passed) {
                    errors.add("t=" + checkpoint + " " + expected.getKey() + " expected="
                            + expected.getValue() + " computed=" + actual + " absoluteError=" + absoluteError
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
            CompiledSchema.OutputValidationDefinition tolerance) {
        if (!Double.isFinite(actual) || !Double.isFinite(expected)) return false;
        if ("exact".equals(tolerance.comparison()) || "discrete".equals(tolerance.comparison())) {
            return Double.doubleToLongBits(actual) == Double.doubleToLongBits(expected);
        }
        return absoluteError <= tolerance.absoluteTolerance() || relativeError <= tolerance.relativeTolerance();
    }

    private List<Double> checkpointsFor(CompiledSchema.ValidationDefinition definition, double duration) {
        double end = Math.max(0.01, duration);
        List<Double> result = new ArrayList<>();
        for (double fraction : definition.checkpointFractions()) result.add(end * fraction);
        if (result.isEmpty()) throw new IllegalStateException("Schema validation checkpoints are missing");
        return List.copyOf(result);
    }

    private double elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000.0;
    }
}
