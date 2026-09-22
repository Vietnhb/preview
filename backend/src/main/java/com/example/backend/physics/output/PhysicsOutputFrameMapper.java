package com.example.backend.physics.output;

import com.example.backend.physics.model.ScalarField;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.validation.OutputSourceBinding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Maps the old grouped solver container into the typed runtime output frame.
 *
 * <p>The mapper is the single boundary for the transitional grouped shape. It
 * does not perform unit inference: callers must provide units from the pinned
 * compiled contract. A missing unit stays missing and is rejected by the typed
 * contract validator.</p>
 */
public final class PhysicsOutputFrameMapper {
    private PhysicsOutputFrameMapper() { }

    public static PhysicsOutputFrame fromSolverOutput(SolverOutput output,
                                                       Map<String, String> declaredUnits) {
        if (output == null) throw new IllegalArgumentException("Solver output is required");
        Map<String, String> units = declaredUnits == null ? Map.of() : Map.copyOf(declaredUnits);
        List<PhysicsOutput> outputs = new ArrayList<>();
        appendSeries(outputs, output.values(), output.time(), units, true);
        // Legacy solver groups reuse component keys (for example `x` appears
        // in position, velocity and acceleration maps), while the typed
        // contract exposes distinct output keys such as x, vx and ax. Prefer
        // an explicitly produced values-series when both shapes carry the
        // same key; otherwise the groups would collide during adaptation.
        appendSeries(outputs, output.positions(), output.time(), units, false, output.values());
        appendSeries(outputs, output.velocities(), output.time(), units, false, output.values());
        appendSeries(outputs, output.accelerations(), output.time(), units, false, output.values());
        if (output.scalarOutputs() != null) {
            output.scalarOutputs().forEach((key, value) -> appendUnique(outputs,
                    new ScalarOutput(key, Optional.ofNullable(units.get(key)), value)));
        }
        if (output.scalarFields() != null) {
            output.scalarFields().forEach((key, field) -> appendUnique(outputs,
                    new ScalarFieldOutput(key, field)));
        }
        return new PhysicsOutputFrame(output.time(), outputs);
    }

    public static PhysicsOutputFrame fromSolverOutput(SolverOutput output,
                                                       PhysicsOutputContract contract) {
        if (contract == null) throw new IllegalArgumentException("Output contract is required");
        Map<String, String> units = new LinkedHashMap<>();
        contract.outputs().forEach((key, definition) -> units.put(key, definition.unit()));
        return fromSolverOutput(output, units);
    }

    /**
     * Projects a typed frame to the legacy grouped response container. This
     * mapper is used only at the API compatibility boundary; numerical and
     * validation code keeps the typed frame as its source of truth.
     */
    public static SolverOutput toSolverOutput(PhysicsOutputFrame frame) {
        return toSolverOutput(frame, Map.of());
    }

    /**
     * Projects a typed frame to the legacy grouped response using compiled
     * catalog source bindings. The binding metadata, rather than schema IDs or
     * topic branches, decides which compatibility group receives a series.
     */
    public static SolverOutput toSolverOutput(PhysicsOutputFrame frame,
                                               Map<String, List<OutputSourceBinding>> sourceBindings) {
        if (frame == null) throw new IllegalArgumentException("Typed output frame is required");
        Map<String, List<OutputSourceBinding>> bindings = sourceBindings == null ? Map.of() : sourceBindings;
        Map<String, List<Double>> values = new LinkedHashMap<>();
        Map<String, List<Double>> positions = new LinkedHashMap<>();
        Map<String, List<Double>> velocities = new LinkedHashMap<>();
        Map<String, List<Double>> accelerations = new LinkedHashMap<>();
        Map<String, ScalarField> fields = new LinkedHashMap<>();
        Map<String, Double> scalars = new LinkedHashMap<>();
        for (PhysicsOutput output : frame.outputs()) {
            if (output instanceof ScalarOutput scalar) {
                putUniqueScalar(scalars, output.key(), scalar.value());
            } else if (output instanceof TimeSeriesOutput series) {
                putUniqueSeries(values, output.key(), series.values());
                projectSeries(output.key(), series.values(), bindings.get(output.key()),
                        positions, velocities, accelerations);
            } else if (output instanceof VectorSeriesOutput vector) {
                for (int index = 0; index < vector.componentKeys().size(); index++) {
                    int component = index;
                    String key = vector.componentKeys().get(index);
                    List<Double> componentValues = vector.values().stream()
                            .map(sample -> sample.get(component)).toList();
                    putUniqueSeries(values, key, componentValues);
                    projectSeries(key, componentValues, bindings.get(key),
                            positions, velocities, accelerations);
                }
            } else if (output instanceof ScalarFieldOutput field) {
                if (fields.putIfAbsent(output.key(), field.field()) != null) {
                    throw new IllegalArgumentException("Conflicting duplicate output key " + output.key());
                }
            } else {
                throw new IllegalArgumentException("Unsupported typed output kind: " + output.kind());
            }
        }
        return new SolverOutput(frame.timeSeconds(), positions, velocities, accelerations, values, fields, scalars);
    }

    private static void projectSeries(String key, List<Double> values,
                                      List<OutputSourceBinding> bindings,
                                      Map<String, List<Double>> positions,
                                      Map<String, List<Double>> velocities,
                                      Map<String, List<Double>> accelerations) {
        if (bindings == null || bindings.isEmpty()) return;
        for (OutputSourceBinding binding : bindings) {
            switch (binding.group()) {
                case POSITIONS, LEGACY_ENTITY_POSITION -> putUniqueSeries(positions, binding.key(), values);
                case VELOCITIES -> putUniqueSeries(velocities, binding.key(), values);
                case ACCELERATIONS -> putUniqueSeries(accelerations, binding.key(), values);
                case VALUES, LEGACY_AUTO -> { }
            }
        }
    }

    private static void putUniqueSeries(Map<String, List<Double>> target, String key, List<Double> values) {
        List<Double> existing = target.putIfAbsent(key, List.copyOf(values));
        if (existing != null && !existing.equals(values)) {
            throw new IllegalArgumentException("Conflicting duplicate output key " + key);
        }
    }

    private static void putUniqueScalar(Map<String, Double> target, String key, double value) {
        Double existing = target.putIfAbsent(key, value);
        if (existing != null && Double.doubleToLongBits(existing) != Double.doubleToLongBits(value)) {
            throw new IllegalArgumentException("Conflicting duplicate output key " + key);
        }
    }

    private static void appendSeries(List<PhysicsOutput> target, Map<String, List<Double>> source,
                                     List<Double> time, Map<String, String> units,
                                     boolean allowScalar) {
        appendSeries(target, source, time, units, allowScalar, Map.of());
    }

    private static void appendSeries(List<PhysicsOutput> target, Map<String, List<Double>> source,
                                     List<Double> time, Map<String, String> units,
                                     boolean allowScalar, Map<String, List<Double>> preferredSeries) {
        if (source == null) return;
        source.forEach((key, values) -> {
            List<Double> preferred = preferredSeries.get(key);
            if (preferred != null) {
                if (!preferred.equals(values)) {
                    throw new IllegalArgumentException("Conflicting duplicate output key " + key);
                }
                return;
            }
            Optional<String> unit = Optional.ofNullable(units.get(key));
            if (allowScalar && values != null && values.size() == 1 && time != null && time.size() == 1) {
                appendUnique(target, new ScalarOutput(key, unit, values.getFirst()));
            } else {
                appendUnique(target, new TimeSeriesOutput(key, unit, time, values));
            }
        });
    }

    private static void appendUnique(List<PhysicsOutput> target, PhysicsOutput candidate) {
        for (PhysicsOutput existing : target) {
            if (!existing.key().equals(candidate.key())) continue;
            if (existing.equals(candidate)) return;
            throw new IllegalArgumentException("Conflicting duplicate output key " + candidate.key());
        }
        target.add(candidate);
    }
}
