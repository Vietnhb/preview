package com.example.backend.physics.compatibility.legacy.solver.thermal;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.model.thermal.FirstLawParameters;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FirstLawSolver implements PhysicsSolver {
    @Override public String solverId() { return "first_law_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                                        double durationSeconds, double stepSeconds) {
        if (!"first_law_thermodynamics".equals(PhysicsValues.model(specification)))
            throw new IllegalArgumentException("Unsupported first-law model: " + PhysicsValues.model(specification));
        FirstLawParameters p = FirstLawParameters.from(specification, overrides);
        int points = Math.min(16_384, Math.max(1, (int) Math.ceil(Math.max(0.01, durationSeconds) / Math.max(0.001, stepSeconds))));
        List<Double> time = new ArrayList<>(points + 1); Map<String, List<Double>> values = new LinkedHashMap<>();
        List<Double> internal = new ArrayList<>(points + 1), delta = new ArrayList<>(points + 1);
        List<Double> heat = new ArrayList<>(points + 1), work = new ArrayList<>(points + 1);
        for (int i = 0; i <= points; i++) { double t = Math.min(Math.max(0.01, durationSeconds), i * Math.max(0.001, stepSeconds));
            time.add(t); internal.add(p.finalInternalEnergy()); delta.add(p.deltaInternalEnergy()); heat.add(p.heatAdded()); work.add(p.workDone()); }
        values.put("internalEnergy", internal); values.put("deltaInternalEnergy", delta); values.put("heat", heat); values.put("work", work);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
