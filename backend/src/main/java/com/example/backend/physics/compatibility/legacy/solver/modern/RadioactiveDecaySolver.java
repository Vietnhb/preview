package com.example.backend.physics.compatibility.legacy.solver.modern;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.compatibility.legacy.model.modern.RadioactiveDecayParameters;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic expectation-value model for radioactive decay. */
@Component
public class RadioactiveDecaySolver implements PhysicsSolver {
    @Override public String solverId() { return "radioactive_decay_solver"; }

    @Override
    public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                              double durationSeconds, double stepSeconds) {
        if (!"radioactive_decay".equals(PhysicsValues.model(specification))) {
            throw new IllegalArgumentException("Unsupported radioactive-decay model: " + PhysicsValues.model(specification));
        }
        RadioactiveDecayParameters parameters = RadioactiveDecayParameters.from(specification, overrides);
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0 || !Double.isFinite(stepSeconds) || stepSeconds <= 0) {
            throw new IllegalArgumentException("durationSeconds and stepSeconds must be finite and positive");
        }
        int points = Math.min(16_384, Math.max(1, (int) Math.ceil(durationSeconds / stepSeconds)));
        List<Double> time = new ArrayList<>(points + 1);
        List<Double> remaining = new ArrayList<>(points + 1);
        List<Double> activity = new ArrayList<>(points + 1);
        for (int index = 0; index <= points; index++) {
            double current = Math.min(durationSeconds, index * stepSeconds);
            double count = parameters.initialCount() * Math.exp(-parameters.decayConstant() * current);
            time.add(current); remaining.add(count); activity.add(parameters.decayConstant() * count);
        }
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("remainingCount", remaining); values.put("activity", activity);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
