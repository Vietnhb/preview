package com.example.backend.service.simulation;

import com.example.backend.service.problem.SchemaDefinitionService;
import com.example.backend.service.problem.CompiledSchema;
import com.example.backend.exception.SolverBindingException;
import com.example.backend.physics.compatibility.LegacyPhysicsExecutionAdapterV1;

import com.example.backend.dto.simulation.ValidationCheckpointResponse;
import com.example.backend.dto.simulation.ValidationResponse;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.compatibility.legacy.reference.ReferenceSolverRegistry;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.BoundPhysicsModule;
import com.example.backend.physics.output.PhysicsOutput;
import com.example.backend.physics.output.PhysicsOutputFrame;
import com.example.backend.physics.output.ScalarOutput;
import com.example.backend.physics.output.ScalarFieldOutput;
import com.example.backend.physics.output.TimeSeriesOutput;
import com.example.backend.physics.output.VectorSeriesOutput;
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
        return validateInternal(specification, schemaId, schemaVersion, numerical, null, overrides, typedModule);
    }

    /**
     * Validates a typed module frame directly.  This is the current runtime
     * boundary for modules that have already produced a contract-shaped
     * result; the grouped SolverOutput overload above remains only for the
     * explicit legacy/HTTP compatibility path.
     */
    public ValidationResponse validateTyped(JsonNode specification, String schemaId, String schemaVersion,
                                            PhysicsOutputFrame numerical, Map<String, Double> overrides,
                                            BoundPhysicsModule typedModule) {
        return validateInternal(specification, schemaId, schemaVersion, null, numerical, overrides, typedModule);
    }

    private ValidationResponse validateInternal(JsonNode specification, String schemaId, String schemaVersion,
                                                 SolverOutput legacyNumerical, PhysicsOutputFrame typedNumerical,
                                                 Map<String, Double> overrides, BoundPhysicsModule typedModule) {
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
        CompiledSchema compiled = schemaDefinitions.compiled(schema, specification);
        PhysicsOutputFrame numericalFrame = typedNumerical;
        if (numericalFrame == null && legacyNumerical == null)
            throw new IllegalArgumentException("Numerical output is required");
        CompiledSchema.ValidationDefinition validationDefinition = compiled.validation();
        double tolerance = validationDefinition.tolerance();
        Map<String, CompiledSchema.OutputValidationDefinition> outputTolerances =
                validationDefinition.outputTolerances();
        List<ValidationCheckpointResponse> checkpoints = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<Double> numericalTimes = numericalFrame == null ? legacyNumerical.time() : numericalFrame.timeSeconds();
        double duration = numericalTimes.isEmpty() ? 0 : numericalTimes.get(numericalTimes.size() - 1);
        for (double checkpoint : checkpointsFor(validationDefinition, duration)) {
            AnalyticalPoint analytical = typedModule == null
                    ? legacySolver.solve(specification, overrides, checkpoint)
                    : typedModule.closedFormReference(checkpoint);
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
                if (!compiled.outputKeys().contains(expected.getKey())) {
                    errors.add("t=" + checkpoint + " reference returned undeclared output: " + expected.getKey());
                    continue;
                }
                CompiledSchema.OutputValidationDefinition contract = outputTolerances.getOrDefault(expected.getKey(),
                        new CompiledSchema.OutputValidationDefinition(tolerance, tolerance, "numeric"));
                double actual = numericalFrame == null
                        ? actualAtLegacy(legacyNumerical, expected.getKey(), checkpoint)
                        : actualAt(numericalFrame, expected.getKey(), checkpoint);
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

    private double actualAt(PhysicsOutputFrame frame, String key, double targetTime) {
        PhysicsOutput output = frame.outputs().stream()
                .filter(candidate -> candidate.key().equals(key))
                .findFirst().orElse(null);
        if (output instanceof ScalarOutput scalar) return scalar.value();
        if (output instanceof TimeSeriesOutput series)
            return interpolate(series.values(), series.timeSeconds(), targetTime);
        if (output instanceof VectorSeriesOutput vector) {
            int component = vector.componentKeys().indexOf(key);
            if (component >= 0) {
                List<Double> values = vector.values().stream().map(sample -> sample.get(component)).toList();
                return interpolate(values, vector.timeSeconds(), targetTime);
            }
        }
        if (output instanceof ScalarFieldOutput) {
            // A field is not a scalar checkpoint. The contract must expose a
            // scalar probe when an independent reference publishes one.
            return Double.NaN;
        }
        return Double.NaN;
    }

    private double actualAtLegacy(SolverOutput output, String key, double targetTime) {
        Double scalar = output.scalarOutputs().get(key);
        if (scalar != null) return scalar;
        return interpolate(output.values().get(key), output.time(), targetTime);
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
