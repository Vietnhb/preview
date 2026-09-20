package com.example.backend.physics.compatibility;

import com.example.backend.physics.model.ScalarField;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.output.PhysicsOutput;
import com.example.backend.physics.output.PhysicsOutputFrame;
import com.example.backend.physics.output.ScalarFieldOutput;
import com.example.backend.physics.output.ScalarOutput;
import com.example.backend.physics.output.TimeSeriesOutput;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Versioned bridge from existing solver transport to the typed output domain. */
public final class LegacySolverOutputAdapter {
    private LegacySolverOutputAdapter() { }

    public static PhysicsOutputFrame adapt(SolverOutput output, Map<String, String> declaredUnits) {
        if (output == null) throw new IllegalArgumentException("Solver output is required");
        Map<String, String> units = declaredUnits == null ? Map.of() : declaredUnits;
        List<PhysicsOutput> outputs = new ArrayList<>();
        appendSeries(outputs, output.values(), output.time(), units, true);
        appendSeries(outputs, output.positions(), output.time(), units, false);
        appendSeries(outputs, output.velocities(), output.time(), units, false);
        appendSeries(outputs, output.accelerations(), output.time(), units, false);
        if (output.scalarOutputs() != null) {
            output.scalarOutputs().forEach((key, value) ->
                    outputs.add(new ScalarOutput(key, Optional.ofNullable(units.get(key)), value)));
        }
        if (output.scalarFields() != null) {
            output.scalarFields().forEach((key, field) -> outputs.add(new ScalarFieldOutput(key, field)));
        }
        return new PhysicsOutputFrame(output.time(), outputs);
    }

    private static void appendSeries(List<PhysicsOutput> target, Map<String, List<Double>> source,
                                     List<Double> time, Map<String, String> units, boolean allowScalar) {
        if (source == null) return;
        source.forEach((key, values) -> {
            Optional<String> unit = Optional.ofNullable(units.get(key));
            if (allowScalar && values != null && values.size() == 1 && time != null && time.size() == 1) {
                target.add(new ScalarOutput(key, unit, values.getFirst()));
            } else {
                target.add(new TimeSeriesOutput(key, unit, time, values));
            }
        });
    }
}
