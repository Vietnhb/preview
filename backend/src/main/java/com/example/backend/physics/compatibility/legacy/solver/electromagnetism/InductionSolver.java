package com.example.backend.physics.compatibility.legacy.solver.electromagnetism;

import com.example.backend.physics.compatibility.legacy.solver.PhysicsSolver;

import com.example.backend.physics.compatibility.legacy.model.electromagnetism.InductionParameters;
import com.example.backend.physics.compatibility.legacy.PhysicsValues;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class InductionSolver implements PhysicsSolver {
    @Override public String solverId() { return "induction_solver"; }
    @Override public SolverOutput solve(JsonNode specification, Map<String, Double> overrides,
                                        double durationSeconds, double stepSeconds) {
        if (!"electromagnetic_induction".equals(PhysicsValues.model(specification)))
            throw new IllegalArgumentException("Unsupported induction model: " + PhysicsValues.model(specification));
        InductionParameters p = InductionParameters.from(specification, overrides);
        int points = Math.min(16_384, Math.max(1, (int) Math.ceil(Math.max(0.01, durationSeconds) / Math.max(0.001, stepSeconds))));
        List<Double> time = new ArrayList<>(points + 1); List<Double> flux = new ArrayList<>(points + 1), emf = new ArrayList<>(points + 1), field = new ArrayList<>(points + 1);
        for (int i = 0; i <= points; i++) { double t = Math.min(Math.max(0.01, durationSeconds), i * Math.max(0.001, stepSeconds));
            time.add(t); field.add(p.magneticField() + p.fieldRate() * t); flux.add((p.magneticField() + p.fieldRate() * t) * p.coilArea() * Math.cos(p.coilAngle())); emf.add(p.inducedEmf()); }
        Map<String, List<Double>> values = new LinkedHashMap<>(); values.put("magneticField", field); values.put("magneticFlux", flux); values.put("inducedEmf", emf);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
